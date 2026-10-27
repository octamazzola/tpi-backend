package utnfc.isi.back.spring.geoapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado de salud y conectividad con el motor OSRM")
public record OsrmStatusDTO(
        @Schema(example = "UP", description = "UP si está disponible, DOWN si falló")
        String status,

        @Schema(example = "http://localhost:5000", description = "URL configurada de OSRM")
        String osrmUrl,

        @Schema(example = "Motor OSRM respondiendo correctamente", description = "Detalle del estado")
        String detalle
) {}
