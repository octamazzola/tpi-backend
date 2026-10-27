package utnfc.isi.back.spring.geoapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utnfc.isi.back.spring.geoapi.dto.RutaRequest;
import utnfc.isi.back.spring.geoapi.dto.RutaResponse;
import utnfc.isi.back.spring.geoapi.service.OsrmService;

@RestController
@RequestMapping("/api/v1/rutas")
@Tag(name = "Rutas", description = "Cálculo de distancias y tiempos de viaje")
public class RutaController {
    private final OsrmService osrmService;

    public RutaController(OsrmService osrmService) {
        this.osrmService = osrmService;
    }

    @PostMapping("/distancia")
    @Operation(summary = "Calcula una ruta", description = "Obtiene la distancia y duración terrestre entre dos puntos usando OSRM")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ruta calculada correctamente"),
            @ApiResponse(responseCode = "400", description = "Coordenadas inválidas"),
            @ApiResponse(responseCode = "502", description = "OSRM no disponible o sin ruta")
    })
    public ResponseEntity<RutaResponse> calcular(@Valid @RequestBody RutaRequest request) {
        return ResponseEntity.ok(osrmService.calcularRuta(request));
    }
}
