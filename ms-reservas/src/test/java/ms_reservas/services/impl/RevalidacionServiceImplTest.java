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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RevalidacionServiceImplTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Cordoba");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

    private ReservaRepository repo;
    private CatalogoClient catalogo;
    private OpenMeteoClient meteo;
    private EmailService email;
    private RevalidacionServiceImpl service;
    private final AtomicLong ids = new AtomicLong();

    @BeforeEach
    void setUp() {
        repo = mock(ReservaRepository.class);
        catalogo = mock(CatalogoClient.class);
        meteo = mock(OpenMeteoClient.class);
        email = mock(EmailService.class);
        Clock clock = Clock.fixed(HOY.atTime(10, 0).atZone(ZONA).toInstant(), ZONA);
        service = new RevalidacionServiceImpl(repo, catalogo, meteo, email, clock);
        when(meteo.obtenerPronostico(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());
    }

    private Reserva reserva(long actividadId, int diasHastaExcursion, int personas, EstadoReserva estado, int minutosCreacion) {
        return Reserva.builder()
                .id(ids.incrementAndGet())
                .actividadId(actividadId)
                .tituloActividad("Act " + actividadId)
                .emailTurista("t@example.com")
                .fechaExcursion(HOY.plusDays(diasHastaExcursion))
                .cantidadPersonas(personas)
                .estado(estado)
                .recordatorioEnviado(false)
                .fechaCreacion(LocalDateTime.of(2026, 11, 1, 10, 0).plusMinutes(minutosCreacion))
                .build();
    }

    private ActividadCatalogoDTO actividad(long id, int cupo, boolean requiereClima) {
        return new ActividadCatalogoDTO(id, "Act " + id, 1000.0, cupo, -31.0, -64.0, "Córdoba", requiereClima,
                null, null, null, null, true);
    }

    private void vigentes(Reserva... reservas) {
        when(repo.findByEstadoInAndFechaExcursionGreaterThanEqual(anyCollection(), eq(HOY))).thenReturn(List.of(reservas));
    }

    @Test
    void actividadDadaDeBajaCancelaTodasSusReservas() {
        Reserva a = reserva(1, 10, 2, EstadoReserva.PENDIENTE, 0);
        Reserva b = reserva(1, 12, 1, EstadoReserva.CONFIRMADA, 1);
        vigentes(a, b);
        when(catalogo.obtenerActividad(1L)).thenThrow(new ResourceNotFoundException("no existe"));

        ResultadoRevalidacionDTO r = service.revalidar();

        assertThat(a.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(b.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(r.cambios()).hasSize(2);
        verify(email, times(2)).notificar(any(), eq(TipoNotificacion.RESERVA_CANCELADA));
    }

    @Test
    void catalogoCaidoNoCancelaNada() {
        Reserva a = reserva(1, 10, 2, EstadoReserva.PENDIENTE, 0);
        vigentes(a);
        when(catalogo.obtenerActividad(1L)).thenThrow(new ResourceAccessException("timeout"));

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
        when(catalogo.obtenerActividad(1L)).thenReturn(actividad(1, 5, false)); // 7 personas > cupo 5

        service.revalidar();

        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(vieja.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(nueva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void pronosticoAdversoRechazaPorClima() {
        Reserva a = reserva(1, 7, 2, EstadoReserva.CONFIRMADA, 0);
        vigentes(a);
        when(catalogo.obtenerActividad(1L)).thenReturn(actividad(1, 10, true));
        when(meteo.obtenerPronostico(anyDouble(), anyDouble(), eq(HOY.plusDays(7))))
                .thenReturn(Optional.of(new PronosticoDia(85, 63, 15.0)));

        service.revalidar();

        assertThat(a.getEstado()).isEqualTo(EstadoReserva.RECHAZADA_POR_CLIMA);
        assertThat(a.getAlertaClimatica()).isTrue();
        verify(email).notificar(a, TipoNotificacion.RESERVA_RECHAZADA_POR_CLIMA);
    }

    @Test
    void reglaDe48HorasRecuerdaYLuegoAnula() {
        Reserva aDosDias = reserva(1, 2, 1, EstadoReserva.PENDIENTE, 0);
        Reserva manana = reserva(1, 1, 1, EstadoReserva.PENDIENTE, 1);
        Reserva confirmadaManana = reserva(1, 1, 1, EstadoReserva.CONFIRMADA, 2);
        vigentes(aDosDias, manana, confirmadaManana);
        when(catalogo.obtenerActividad(1L)).thenReturn(actividad(1, 10, false));

        ResultadoRevalidacionDTO r = service.revalidar();

        assertThat(aDosDias.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(aDosDias.getRecordatorioEnviado()).isTrue();
        assertThat(manana.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(confirmadaManana.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(r.recordatoriosEnviados()).isEqualTo(1);

        // Una segunda ejecución no reenvía el recordatorio
        service.revalidar();
        verify(email, times(1)).notificar(aDosDias, TipoNotificacion.RECORDATORIO_CONFIRMACION);
    }
}
