package ms_reservas.dtos.request;

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
    private Double latitud;

    @NotNull(message = "La longitud de origen es obligatoria")
    private Double longitud;

    private String direccion;
}
