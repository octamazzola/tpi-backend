package ms_reservas.clients;

import lombok.RequiredArgsConstructor;
import ms_reservas.dtos.external.RutaGeoRequestDTO;
import ms_reservas.dtos.external.RutaGeoResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia geoapi (8083) para calcular distancia y duración del traslado.
 */
@Component
@RequiredArgsConstructor
public class GeoApiClient {

    private final RestClient restClient;

    @Value("${servicios.geoapi.url}")
    private String geoapiUrl;

    public RutaGeoResponseDTO calcularDistancia(RutaGeoRequestDTO request) {
        return restClient.post()
                .uri(geoapiUrl + "/rutas/distancia")
                .body(request)
                .retrieve()
                .body(RutaGeoResponseDTO.class);
    }
}
