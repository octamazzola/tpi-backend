package ms_catalogo.dtos;

import lombok.*;

/**
 * DTO para responder la información de una categoría al cliente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
}
