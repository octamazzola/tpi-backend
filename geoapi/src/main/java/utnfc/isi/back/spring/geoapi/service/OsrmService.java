package utnfc.isi.back.spring.geoapi.service;

import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import utnfc.isi.back.spring.geoapi.dto.RutaRequest;
import utnfc.isi.back.spring.geoapi.dto.RutaResponse;
import utnfc.isi.back.spring.geoapi.exception.OsrmUnavailableException;
import utnfc.isi.back.spring.geoapi.osrm.OsrmRouteResponse;

@Service
public class OsrmService {
    private final RestClient restClient;

    public OsrmService(RestClient osrmRestClient) {
        this.restClient = osrmRestClient;
    }

    public RutaResponse calcularRuta(RutaRequest request) {
        String coordinates = String.format(Locale.ROOT, "%.8f,%.8f;%.8f,%.8f",
                request.longitudOrigen(), request.latitudOrigen(),
                request.longitudDestino(), request.latitudDestino());
        OsrmRouteResponse osrm;
        try {
            osrm = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/route/v1/driving/{coordinates}")
                            .queryParam("overview", "false")
                            .build(coordinates))
                    .retrieve()
                    .body(OsrmRouteResponse.class);
        } catch (RestClientException ex) {
            throw new OsrmUnavailableException("No fue posible consultar el motor de rutas OSRM", ex);
        }

        if (osrm == null || !"Ok".equalsIgnoreCase(osrm.code())
                || osrm.routes() == null || osrm.routes().isEmpty()
                || osrm.routes().getFirst() == null) {
            throw new OsrmUnavailableException("OSRM no encontró una ruta entre las coordenadas indicadas");
        }

        OsrmRouteResponse.Route route = osrm.routes().getFirst();
        long seconds = Math.max(0, Math.round(route.duration()));
        long minutes = Math.round(seconds / 60.0);
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return new RutaResponse(
                route.distance(),
                Math.round(route.distance() / 10.0) / 100.0,
                seconds,
                minutes,
                "%d hs %d min".formatted(hours, remainingMinutes));
    }
}
