package ms_reservas.dtos.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Coordenadas y dirección de origen del turista, usadas para estimar el traslado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrigenTuristaDTO {

    @NotNull(message = "La latitud de origen es obligatoria")
    @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
    private Double latitud;

    @NotNull(message = "La longitud de origen es obligatoria")
    @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
    private Double longitud;

    private String direccion;
}
