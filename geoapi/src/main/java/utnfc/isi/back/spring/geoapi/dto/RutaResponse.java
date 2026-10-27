package utnfc.isi.back.spring.geoapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Distancia y duración calculadas por OSRM")
public record RutaResponse(
        @Schema(example = "669843.5") double distanciaMetros,
        @Schema(example = "669.84") double distanciaKm,
        @Schema(example = "28800") long duracionSegundos,
        @Schema(example = "480") long duracionMinutos,
        @Schema(example = "8 hs 0 min") String duracionFormateada) {
}
