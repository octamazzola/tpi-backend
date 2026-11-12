package ms_reservas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.entities.EstadoReserva;
import ms_reservas.entities.Participante;
import ms_reservas.entities.Reserva;
import ms_reservas.repositories.ReservaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Componente que se ejecuta al iniciar la aplicación (CommandLineRunner).
 * Carga reservas de prueba (referenciando las actividades semilla de ms-catalogo)
 * para poder probar los endpoints desde Swagger UI o Postman.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final ReservaRepository reservaRepository;
    private final Clock clock;

    @Override
    public void run(String... args) {
        if (reservaRepository.count() > 0) {
            return;
        }
        log.info("Iniciando la carga de datos semilla para ms-reservas...");
        LocalDate hoy = LocalDate.now(clock);

        Reserva pendiente = reserva("turista1", 1L, "Ascenso al Cerro Champaquí", hoy.plusDays(10), 45000.0,
                EstadoReserva.PENDIENTE, "Pendiente de confirmación por el prestador", 150.5, "2 hs 30 min",
                participante("Lucía Fernández", "38123456", LocalDate.of(1994, 5, 12)),
                participante("Martín Fernández", "37987654", LocalDate.of(1992, 9, 3)));

        Reserva confirmada = reserva("turista1", 2L, "Paseo Náutico en Dique San Roque", hoy.plusDays(5), 18000.0,
                EstadoReserva.CONFIRMADA, "Confirmada por el prestador", 35.2, "0 hs 45 min",
                participante("Lucía Fernández", "38123456", LocalDate.of(1994, 5, 12)),
                participante("Martín Fernández", "37987654", LocalDate.of(1992, 9, 3)),
                participante("Sofía Fernández", "52111222", LocalDate.of(2014, 1, 20)),
                participante("Tomás Fernández", "54333444", LocalDate.of(2016, 7, 8)));

        Reserva rechazada = reserva("turista2", 4L, "Escalada y Rappel en Los Gigantes", hoy.plusDays(3), 38000.0,
                EstadoReserva.RECHAZADA_POR_CLIMA, "Pronóstico adverso para la fecha de la excursión", 80.0, "1 hs 20 min",
                participante("Diego Pérez", "30111222", LocalDate.of(1985, 2, 14)),
                participante("Ana Gómez", "31222333", LocalDate.of(1987, 11, 30)),
                participante("Pablo Ruiz", "32333444", LocalDate.of(1990, 6, 1)));
        rechazada.setAlertaClimatica(true);
        rechazada.setDescripcionClima("Probabilidad de lluvia: 85%, código de clima: 63");

        // A 2 días de la excursión y todavía PENDIENTE: la revalidación le envía el recordatorio de 48 hs
        Reserva porVencer = reserva("turista1", 3L, "Cabalgata al Peñón del Águila y Bosques Suizos", hoy.plusDays(2), 32000.0,
                EstadoReserva.PENDIENTE, "Pendiente de confirmación por el prestador", 92.4, "1 hs 50 min",
                participante("Lucía Fernández", "38123456", LocalDate.of(1994, 5, 12)));

        reservaRepository.saveAll(List.of(pendiente, confirmada, rechazada, porVencer));
        log.info("Reservas precargadas con éxito (4 registros).");
        log.info("--> Documentación OpenAPI disponible en: http://localhost:8082/swagger-ui.html");
    }

    private Reserva reserva(String turista, Long actividadId, String titulo, LocalDate fecha, double precio,
                            EstadoReserva estado, String motivo, double km, String duracion, Participante... participantes) {
        return Reserva.builder()
                .turistaId(turista)
                .emailTurista(turista + "@example.com")
                .actividadId(actividadId)
                .tituloActividad(titulo)
                .fechaExcursion(fecha)
                .cantidadPersonas(participantes.length)
                .participantes(new ArrayList<>(List.of(participantes)))
                .montoTotal(precio * participantes.length)
                .estado(estado)
                .motivoEstado(motivo)
                .distanciaKm(km)
                .duracionEstimadaViaje(duracion)
                .alertaClimatica(false)
                .fechaCreacion(LocalDateTime.now(clock))
                .build();
    }

    private Participante participante(String nombre, String dni, LocalDate nacimiento) {
        return Participante.builder().nombre(nombre).dni(dni).fechaNacimiento(nacimiento).build();
    }
}
