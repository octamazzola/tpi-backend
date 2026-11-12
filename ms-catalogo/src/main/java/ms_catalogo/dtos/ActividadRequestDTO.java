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

    @NotBlank(message = "La ciudad del punto de encuentro es obligatoria")
    @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
    private String ciudad;

    @NotNull(message = "Debe indicar si la actividad requiere buen clima")
    private Boolean requiereBuenClima;

    @Min(value = 0, message = "La edad mínima no puede ser negativa")
    @Max(value = 120, message = "La edad mínima no puede superar los 120 años")
    private Integer edadMinima;

    @Min(value = 0, message = "La edad máxima no puede ser negativa")
    @Max(value = 120, message = "La edad máxima no puede superar los 120 años")
    private Integer edadMaxima;

    @Min(value = 1, message = "El mes de inicio de temporada debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes de inicio de temporada debe estar entre 1 y 12")
    private Integer mesInicioTemporada;

    @Min(value = 1, message = "El mes de fin de temporada debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes de fin de temporada debe estar entre 1 y 12")
    private Integer mesFinTemporada;
}
