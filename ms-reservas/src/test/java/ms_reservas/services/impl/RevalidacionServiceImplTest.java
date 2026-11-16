package ms_reservas.services.impl;

import ar.edu.utn.frc.bda.common.exceptions.ResourceNotFoundException;
import ms_reservas.clients.CatalogoClient;
import ms_reservas.clients.OpenMeteoClient;
import ms_reservas.clients.OpenMeteoClient.PronosticoDia;
import ms_reservas.dtos.external.ActividadCatalogoDTO;
import ms_reservas.dtos.response.ResultadoRevalidacionDTO;
import ms_reservas.entities.EstadoReserva;
import ms_reservas.entities.Reserva;
import ms_reservas.repositories.ReservaRepository;
import ms_reservas.services.EmailService;
import ms_reservas.services.EmailService.TipoNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RevalidacionServiceImplTest {

    private final LocalDate hoy = LocalDate.now();

    private ReservaRepository repo;
    private Function<Long, ActividadCatalogoDTO> respuestaCatalogo;
    private Function<LocalDate, Optional<PronosticoDia>> respuestaClima;
    private List<NotificacionRegistrada> notificaciones;
    private RevalidacionServiceImpl service;
    private final AtomicLong ids = new AtomicLong();

    private record NotificacionRegistrada(Reserva reserva, TipoNotificacion tipo) {}

    @BeforeEach
    void setUp() {
        repo = mock(ReservaRepository.class);
        notificaciones = new ArrayList<>();
        respuestaCatalogo = id -> actividad(id, 10, false);
        respuestaClima = fecha -> Optional.empty();

        CatalogoClient catalogo = new CatalogoClient(null) {
            @Override
            public ActividadCatalogoDTO obtenerActividad(Long id) {
                return respuestaCatalogo.apply(id);
            }
        };

        OpenMeteoClient meteo = new OpenMeteoClient(null) {
            @Override
            public Optional<PronosticoDia> obtenerPronostico(double latitud, double longitud, LocalDate fecha) {
                return respuestaClima.apply(fecha);
            }
        };

        EmailService email = (reserva, tipo) -> notificaciones.add(new NotificacionRegistrada(reserva, tipo));

        service = new RevalidacionServiceImpl(repo, catalogo, meteo, email);
    }

    private Reserva reserva(long actividadId, int diasHastaExcursion, int personas, EstadoReserva estado, int minutosCreacion) {
        return Reserva.builder()
                .id(ids.incrementAndGet())
                .actividadId(actividadId)
                .tituloActividad("Act " + actividadId)
                .emailTurista("t@example.com")
                .fechaExcursion(hoy.plusDays(diasHastaExcursion))
                .cantidadPersonas(personas)
                .estado(estado)
                .recordatorioEnviado(false)
                .fechaCreacion(LocalDateTime.now().minusDays(5).plusMinutes(minutosCreacion))
                .build();
    }

    private ActividadCatalogoDTO actividad(long id, int cupo, boolean requiereClima) {
        return new ActividadCatalogoDTO(id, "Act " + id, 1000.0, cupo, -31.0, -64.0, "Córdoba", requiereClima,
                null, null, null, null, true);
    }

    private void vigentes(Reserva... reservas) {
        when(repo.findByEstadoInAndFechaExcursionGreaterThanEqual(anyCollection(), eq(hoy))).thenReturn(List.of(reservas));
        when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void actividadDadaDeBajaCancelaTodasSusReservas() {
        Reserva a = reserva(1, 10, 2, EstadoReserva.PENDIENTE, 0);
        Reserva b = reserva(1, 12, 1, EstadoReserva.CONFIRMADA, 1);
        vigentes(a, b);
        respuestaCatalogo = id -> {
            throw new ResourceNotFoundException("no existe");
        };

        ResultadoRevalidacionDTO r = service.revalidar();

        assertThat(a.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(b.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(r.cambios()).hasSize(2);
        assertThat(notificaciones).filteredOn(n -> n.tipo() == TipoNotificacion.RESERVA_CANCELADA).hasSize(2);
    }

    @Test
    void catalogoCaidoNoCancelaNada() {
        Reserva a = reserva(1, 10, 2, EstadoReserva.PENDIENTE, 0);
        vigentes(a);
        respuestaCatalogo = id -> {
            throw new ResourceAccessException("timeout");
        };

        ResultadoRevalidacionDTO r = service.revalidar();

        assertThat(a.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(r.actividadesNoVerificadas()).isEqualTo(1);
        assertThat(r.cambios()).isEmpty();
    }

    @Test
    void reduccionDeCupoCancelaLasPendientesMasRecientesYRespetaConfirmadas() {
        Reserva confirmada = reserva(1, 10, 3, EstadoReserva.CONFIRMADA, 0);
        Reserva vieja = reserva(1, 10, 2, EstadoReserva.PENDIENTE, 1);
        Reserva nueva = reserva(1, 10, 2, EstadoReserva.PENDIENTE, 2);
        vigentes(confirmada, vieja, nueva);
        respuestaCatalogo = id -> actividad(1, 5, false); // 7 personas > cupo 5

        service.revalidar();

        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(vieja.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(nueva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void pronosticoAdversoRechazaPorClima() {
        Reserva a = reserva(1, 7, 2, EstadoReserva.CONFIRMADA, 0);
        vigentes(a);
        respuestaCatalogo = id -> actividad(1, 10, true);
        respuestaClima = fecha -> fecha.equals(hoy.plusDays(7))
                ? Optional.of(new PronosticoDia(85, 63, 15.0))
                : Optional.empty();

        service.revalidar();

        assertThat(a.getEstado()).isEqualTo(EstadoReserva.RECHAZADA_POR_CLIMA);
        assertThat(a.getAlertaClimatica()).isTrue();
        assertThat(notificaciones).containsExactly(new NotificacionRegistrada(a, TipoNotificacion.RESERVA_RECHAZADA_POR_CLIMA));
    }

    @Test
    void reglaDe48HorasRecuerdaYLuegoAnula() {
        Reserva aDosDias = reserva(1, 2, 1, EstadoReserva.PENDIENTE, 0);
        Reserva manana = reserva(1, 1, 1, EstadoReserva.PENDIENTE, 1);
        Reserva confirmadaManana = reserva(1, 1, 1, EstadoReserva.CONFIRMADA, 2);
        vigentes(aDosDias, manana, confirmadaManana);
        respuestaCatalogo = id -> actividad(1, 10, false);

        ResultadoRevalidacionDTO r = service.revalidar();

        assertThat(aDosDias.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(aDosDias.getRecordatorioEnviado()).isTrue();
        assertThat(manana.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(confirmadaManana.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(r.recordatoriosEnviados()).isEqualTo(1);

        // Una segunda ejecución no reenvía el recordatorio
        service.revalidar();
        assertThat(notificaciones)
                .filteredOn(n -> n.reserva() == aDosDias && n.tipo() == TipoNotificacion.RECORDATORIO_CONFIRMACION)
                .hasSize(1);
    }
}
