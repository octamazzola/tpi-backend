package ms_reservas.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.dtos.external.RutaGeoRequestDTO;
import ms_reservas.dtos.external.RutaGeoResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Cliente HTTP hacia geoapi (8083) para calcular distancia y duración del traslado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeoApiClient {

    private final RestClient restClient;

    @Value("${servicios.geoapi.url}")
    private String geoapiUrl;

    /**
     * Devuelve la ruta calculada, o vacío si geoapi/OSRM no está disponible (fallback):
     * la reserva no se cae por no poder estimar el traslado.
     */
    public Optional<RutaGeoResponseDTO> calcularDistancia(RutaGeoRequestDTO request) {
        try {
            return Optional.ofNullable(restClient.post()
                    .uri(geoapiUrl + "/rutas/distancia")
                    .body(request)
                    .retrieve()
                    .body(RutaGeoResponseDTO.class));
        } catch (RestClientException e) {
            log.warn("geoapi no disponible, la reserva se registra sin traslado estimado: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
