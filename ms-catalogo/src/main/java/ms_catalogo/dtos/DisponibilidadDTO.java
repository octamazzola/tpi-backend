package ms_catalogo.dtos;

import lombok.*;

/**
 * DTO para consultar la disponibilidad de cupos de una actividad turística.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibilidadDTO {

    private Long actividadId;
    private String titulo;
    private Integer cupoMaximo;
    private Boolean disponible;
}
