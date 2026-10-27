package ms_catalogo.dtos;

import jakarta.validation.constraints.*;
import lombok.*;
import ms_catalogo.entities.Dificultad;

/**
 * DTO que representa el JSON recibido al crear o actualizar una Actividad (POST/PUT).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActividadRequestDTO {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 150, message = "El título no puede superar los 150 caracteres")
    private String titulo;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String descripcion;

    @NotNull(message = "El ID de la categoría es obligatorio")
    private Long categoriaId;

    @NotNull(message = "La dificultad es obligatoria (BAJA, MEDIA o ALTA)")
    private Dificultad dificultad;

    @NotNull(message = "El precio base es obligatorio")
    @Positive(message = "El precio base debe ser mayor a 0")
    private Double precioBase;

    @NotNull(message = "El cupo máximo es obligatorio")
    @Min(value = 1, message = "El cupo máximo debe ser de al menos 1 persona")
    private Integer cupoMaximo;

    @NotNull(message = "La latitud de inicio es obligatoria")
    private Double latitudInicio;

    @NotNull(message = "La longitud de inicio es obligatoria")
    private Double longitudInicio;

    @Size(max = 255, message = "La dirección del punto de encuentro no puede superar los 255 caracteres")
    private String direccionPuntoEncuentro;

    @NotNull(message = "Debe indicar si la actividad requiere buen clima")
    private Boolean requiereBuenClima;
}
