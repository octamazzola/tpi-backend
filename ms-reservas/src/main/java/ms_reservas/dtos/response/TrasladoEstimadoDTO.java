package ms_reservas.dtos.response;

import lombok.*;

/**
 * Distancia y duración del viaje del turista hasta el punto de encuentro (calculadas por OSRM).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrasladoEstimadoDTO {

    private Double distanciaKm;
    private String duracionFormateada;
}
