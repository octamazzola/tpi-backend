package utnfc.isi.back.spring.geoapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Punto geográfico con latitud y longitud")
public record CoordenadaDTO(
        @NotNull(message = "La latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "La latitud mínima es -90.0")
        @DecimalMax(value = "90.0", message = "La latitud máxima es 90.0")
        @Schema(example = "-31.4135", description = "Latitud en grados decimales")
        Double latitud,

        @NotNull(message = "La longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "La longitud mínima es -180.0")
        @DecimalMax(value = "180.0", message = "La longitud máxima es 180.0")
        @Schema(example = "-64.18105", description = "Longitud en grados decimales")
        Double longitud
) {}
