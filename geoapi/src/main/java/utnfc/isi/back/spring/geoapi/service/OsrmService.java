package utnfc.isi.back.spring.geoapi.service;

import org.springframework.stereotype.Service;
import utnfc.isi.back.spring.geoapi.dto.RutaRequest;
import utnfc.isi.back.spring.geoapi.dto.RutaResponse;

/**
 * Servicio delegado para mantener compatibilidad hacia atrás con el código previo de Álvaro.
 */
@Service
public class OsrmService {

    private final RutaService rutaService;

    public OsrmService(RutaService rutaService) {
        this.rutaService = rutaService;
    }

    public RutaResponse calcularRuta(RutaRequest request) {
        return rutaService.calcularRuta(request);
    }
}
