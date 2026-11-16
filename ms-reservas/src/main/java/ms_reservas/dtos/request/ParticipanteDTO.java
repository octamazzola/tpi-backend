package ms_reservas.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

/**
 * Datos de una persona que asiste a la excursión. Se usa tanto en el request como en la respuesta.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipanteDTO {

    @NotBlank(message = "El nombre del participante es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
    private String nombre;

    @NotBlank(message = "El DNI del participante es obligatorio")
    @Pattern(regexp = "\\d{7,8}", message = "El DNI debe tener 7 u 8 dígitos, sin puntos")
    private String dni;

    @NotNull(message = "La fecha de nacimiento del participante es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser una fecha pasada")
    private LocalDate fechaNacimiento;

    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    private String telefono;
}
