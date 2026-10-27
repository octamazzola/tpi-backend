package utnfc.isi.back.spring.geoapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Petición de cálculo de ruta entre dos puntos geográficos estructurados (origen y destino)")
public record RutaRequest(
        @NotNull(message = "El punto de origen es obligatorio")
        @Valid
        @Schema(description = "Punto geográfico de partida (Latitud y Longitud)")
        CoordenadaDTO origen,

        @NotNull(message = "El punto de destino es obligatorio")
        @Valid
        @Schema(description = "Punto geográfico de llegada (Latitud y Longitud)")
        CoordenadaDTO destino
) {}
