package ms_reservas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.entities.EstadoReserva;
import ms_reservas.entities.Reserva;
import ms_reservas.repositories.ReservaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Override
    public void run(String... args) {
        if (reservaRepository.count() == 0) {
            log.info("Iniciando la carga de datos semilla para ms-reservas...");

            Reserva pendiente = Reserva.builder()
                    .turistaId("turista-1")
                    .emailTurista("turista1@example.com")
                    .actividadId(1L)
                    .tituloActividad("Ascenso al Cerro Champaquí")
                    .fechaExcursion(LocalDate.now().plusDays(10))
                    .cantidadPersonas(2)
                    .montoTotal(90000.0)
                    .estado(EstadoReserva.PENDIENTE)
                    .distanciaKm(150.5)
                    .duracionEstimadaViaje("2 hs 30 min")
                    .alertaClimatica(false)
                    .fechaCreacion(LocalDateTime.now())
                    .build();

            Reserva confirmada = Reserva.builder()
                    .turistaId("turista-1")
                    .emailTurista("turista1@example.com")
                    .actividadId(2L)
                    .tituloActividad("Paseo Náutico en Dique San Roque")
                    .fechaExcursion(LocalDate.now().plusDays(5))
                    .cantidadPersonas(4)
                    .montoTotal(72000.0)
                    .estado(EstadoReserva.CONFIRMADA)
                    .distanciaKm(35.2)
                    .duracionEstimadaViaje("0 hs 45 min")
                    .alertaClimatica(false)
                    .fechaCreacion(LocalDateTime.now())
                    .build();

            Reserva rechazada = Reserva.builder()
                    .turistaId("turista-2")
                    .emailTurista("turista2@example.com")
                    .actividadId(4L)
                    .tituloActividad("Escalada y Rappel en Los Gigantes")
                    .fechaExcursion(LocalDate.now().plusDays(3))
                    .cantidadPersonas(3)
                    .montoTotal(114000.0)
                    .estado(EstadoReserva.RECHAZADA_POR_CLIMA)
                    .distanciaKm(80.0)
                    .duracionEstimadaViaje("1 hs 20 min")
                    .alertaClimatica(true)
                    .descripcionClima("Probabilidad de lluvia: 85%, código de clima: 63")
                    .fechaCreacion(LocalDateTime.now())
                    .build();

            reservaRepository.saveAll(List.of(pendiente, confirmada, rechazada));
            log.info("Reservas precargadas con éxito (3 registros).");
            log.info("--> Documentación OpenAPI disponible en: http://localhost:8082/swagger-ui.html");
        }
    }
}
