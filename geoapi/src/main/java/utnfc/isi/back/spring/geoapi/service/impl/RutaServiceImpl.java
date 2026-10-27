package utnfc.isi.back.spring.geoapi.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import utnfc.isi.back.spring.geoapi.config.OsrmProperties;
import utnfc.isi.back.spring.geoapi.dto.OsrmStatusDTO;
import utnfc.isi.back.spring.geoapi.dto.RutaRequest;
import utnfc.isi.back.spring.geoapi.dto.RutaResponse;
import utnfc.isi.back.spring.geoapi.exception.OsrmUnavailableException;
import utnfc.isi.back.spring.geoapi.osrm.OsrmRouteResponse;
import utnfc.isi.back.spring.geoapi.service.RutaService;

import java.util.Locale;

/**
 * Implementación de RutaService comunicándose con el motor OSRM vía RestClient.
 */
@Service
public class RutaServiceImpl implements RutaService {

    private final RestClient restClient;
    private final OsrmProperties properties;

    public RutaServiceImpl(RestClient osrmRestClient, OsrmProperties properties) {
        this.restClient = osrmRestClient;
        this.properties = properties;
    }

    @Override
    public RutaResponse calcularRuta(RutaRequest request) {
        double latOrig = request.origen().latitud();
        double lonOrig = request.origen().longitud();
        double latDest = request.destino().latitud();
        double lonDest = request.destino().longitud();

        return estimarRuta(latOrig, lonOrig, latDest, lonDest);
    }

    @Override
    public RutaResponse estimarRuta(double latOrig, double lonOrig, double latDest, double lonDest) {
        // OSRM exige por especificación estándar: {longitud},{latitud};{longitud},{latitud}
        String coordinates = String.format(Locale.ROOT, "%.8f,%.8f;%.8f,%.8f",
                lonOrig, latOrig,
                lonDest, latDest);

        OsrmRouteResponse osrm;
        try {
            osrm = restClient.get()
                    .uri("/route/v1/driving/" + coordinates + "?overview=false")
                    .retrieve()
                    .body(OsrmRouteResponse.class);
        } catch (RestClientException ex) {
            ex.printStackTrace();
            throw new OsrmUnavailableException("No fue posible consultar el motor de rutas OSRM: " + ex.getMessage(), ex);
        }

        if (osrm == null || !"Ok".equalsIgnoreCase(osrm.code())
                || osrm.routes() == null || osrm.routes().isEmpty()
                || osrm.routes().getFirst() == null) {
            throw new OsrmUnavailableException("OSRM no encontró una ruta terrestre entre las coordenadas indicadas");
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
                "%d hs %d min".formatted(hours, remainingMinutes)
        );
    }

    @Override
    public OsrmStatusDTO checkOsrmHealth() {
        try {
            // Consulta de prueba sencilla para verificar conectividad con OSRM
            String testCoords = "-64.18105,-31.4135;-64.18200,-31.4140";
            OsrmRouteResponse testResp = restClient.get()
                    .uri("/route/v1/driving/" + testCoords + "?overview=false")
                    .retrieve()
                    .body(OsrmRouteResponse.class);

            if (testResp != null && "Ok".equalsIgnoreCase(testResp.code())) {
                return new OsrmStatusDTO("UP", properties.baseUrl(), "Motor OSRM respondiendo correctamente en Docker");
            }
            return new OsrmStatusDTO("DEGRADED", properties.baseUrl(), "OSRM respondió con código: " + (testResp != null ? testResp.code() : "NULL"));
        } catch (Exception ex) {
            return new OsrmStatusDTO("DOWN", properties.baseUrl(), "Fallo de conexión con OSRM: " + ex.getMessage());
        }
    }
}
