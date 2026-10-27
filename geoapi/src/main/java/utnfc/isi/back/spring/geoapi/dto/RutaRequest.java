package utnfc.isi.back.spring.geoapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Coordenadas geográficas de origen y destino")
public record RutaRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        @Schema(example = "-31.4135") Double latitudOrigen,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        @Schema(example = "-64.18105") Double longitudOrigen,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        @Schema(example = "-32.9471") Double latitudDestino,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        @Schema(example = "-60.6985") Double longitudDestino) {
}
