package ms_catalogo.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ms_catalogo.entities.Dificultad;

/**
 * DTO para actualizaciones parciales (HTTP PATCH) de una actividad.
 * Todos los campos son opcionales; solo se modifican aquellos atributos que vengan no nulos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActividadPatchDTO {

    @Size(max = 150, message = "El título no puede superar los 150 caracteres")
    private String titulo;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String descripcion;

    private Long categoriaId;

    private Dificultad dificultad;

    @Positive(message = "El precio base debe ser un valor positivo mayor a 0")
    private Double precioBase;

    @Min(value = 1, message = "El cupo máximo debe ser al menos de 1 participante")
    private Integer cupoMaximo;

    private Double latitudInicio;

    private Double longitudInicio;

    @Size(max = 255, message = "La dirección del punto de encuentro no puede superar los 255 caracteres")
    private String direccionPuntoEncuentro;

    private Boolean requiereBuenClima;
}
