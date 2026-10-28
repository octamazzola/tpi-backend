package ms_reservas.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.dtos.external.OpenMeteoResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Cliente HTTP hacia la API pública Open-Meteo con fallback ante fallas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenMeteoClient {

    /** Pronóstico del día de la excursión. */
    public record PronosticoDia(int probabilidadLluvia, int weatherCode, Double temperaturaMax) {}

    private final RestClient restClient;

    @Value("${servicios.openmeteo.url}")
    private String openMeteoUrl;

    /**
     * Devuelve el pronóstico de la fecha, o vacío si Open-Meteo falla (timeout, sin internet)
     * o no tiene datos para esa fecha. Nunca lanza excepción: el flujo de reserva continúa.
     */
    public Optional<PronosticoDia> obtenerPronostico(double latitud, double longitud, LocalDate fecha) {
        try {
            OpenMeteoResponseDTO respuesta = restClient.get()
                    .uri(openMeteoUrl + "?latitude={lat}&longitude={lon}"
                            + "&daily=weather_code,precipitation_probability_max,temperature_2m_max"
                            + "&timezone=America/Argentina/Cordoba",
                            latitud, longitud)
                    .retrieve()
                    .body(OpenMeteoResponseDTO.class);

            if (respuesta == null || respuesta.daily() == null || respuesta.daily().time() == null) {
                return Optional.empty();
            }

            var daily = respuesta.daily();
            int idx = daily.time().indexOf(fecha.toString());
            if (idx < 0) {
                log.warn("Open-Meteo no devolvió pronóstico para la fecha {}", fecha);
                return Optional.empty();
            }

            Integer prob = daily.precipitationProbabilityMax() != null ? daily.precipitationProbabilityMax().get(idx) : null;
            Integer code = daily.weatherCode() != null ? daily.weatherCode().get(idx) : null;
            Double temp = daily.temperature2mMax() != null ? daily.temperature2mMax().get(idx) : null;

            return Optional.of(new PronosticoDia(prob != null ? prob : 0, code != null ? code : 0, temp));
        } catch (Exception e) {
            log.warn("Open-Meteo no disponible, se continúa sin alerta climática: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
