package utnfc.isi.back.spring.geoapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import utnfc.isi.back.spring.geoapi.dto.OsrmStatusDTO;
import utnfc.isi.back.spring.geoapi.dto.RutaRequest;
import utnfc.isi.back.spring.geoapi.dto.RutaResponse;
import utnfc.isi.back.spring.geoapi.service.RutaService;

@RestController
// Mapea tanto /api/v1/geo (consumo vía API Gateway) como /api/v1 (consumo directo)
@RequestMapping({"/api/v1/geo", "/api/v1"})
@CrossOrigin(origins = "*")
@Tag(name = "GeoAPI / Rutas", description = "Cálculo de trayectos, distancias y tiempos de traslado terrestre con OSRM")
public class RutaController {

    private final RutaService rutaService;

    public RutaController(RutaService rutaService) {
        this.rutaService = rutaService;
    }

    @PostMapping("/rutas/distancia")
    @Operation(summary = "Calcular ruta terrestre (POST)", description = "Obtiene distancia exacta y duración de viaje entre origen y destino mediante OSRM")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ruta calculada correctamente"),
            @ApiResponse(responseCode = "400", description = "Coordenadas inválidas o incompletas"),
            @ApiResponse(responseCode = "502", description = "Motor OSRM no disponible o ruta no encontrada")
    })
    public ResponseEntity<RutaResponse> calcularDistancia(@Valid @RequestBody RutaRequest request) {
        return ResponseEntity.ok(rutaService.calcularRuta(request));
    }

    @GetMapping("/rutas/estimacion")
    @Operation(summary = "Estimación rápida de ruta (GET)", description = "Calcula distancia y duración a partir de coordenadas enviadas como Query Parameters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estimación calculada correctamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros de coordenadas inválidos"),
            @ApiResponse(responseCode = "502", description = "Motor OSRM no disponible o ruta no encontrada")
    })
    public ResponseEntity<RutaResponse> estimarRuta(
            @Parameter(description = "Latitud de origen", example = "-31.4135") @RequestParam double latOrig,
            @Parameter(description = "Longitud de origen", example = "-64.18105") @RequestParam double lonOrig,
            @Parameter(description = "Latitud de destino", example = "-31.9868") @RequestParam double latDest,
            @Parameter(description = "Longitud de destino", example = "-64.9351") @RequestParam double lonDest
    ) {
        return ResponseEntity.ok(rutaService.estimarRuta(latOrig, lonOrig, latDest, lonDest));
    }

    @GetMapping("/health-osrm")
    @Operation(summary = "Health Check de OSRM", description = "Verifica la conectividad y respuesta del motor OSRM corriendo en Docker")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chequeo ejecutado (indica estado UP, DEGRADED o DOWN)")
    })
    public ResponseEntity<OsrmStatusDTO> healthOsrm() {
        return ResponseEntity.ok(rutaService.checkOsrmHealth());
    }
}
