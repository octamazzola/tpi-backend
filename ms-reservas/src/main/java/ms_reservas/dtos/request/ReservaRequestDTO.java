package ms_reservas.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

/**
 * DTO de entrada para crear una reserva (POST /api/v1/reservas).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaRequestDTO {

    @NotBlank(message = "El identificador del turista es obligatorio")
    @Size(max = 100, message = "El identificador del turista no puede superar los 100 caracteres")
    private String turistaId;

    @NotBlank(message = "El email del turista es obligatorio")
    @Email(message = "El email del turista no tiene un formato válido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String emailTurista;

    @NotNull(message = "La actividad es obligatoria")
    private Long actividadId;

    @NotNull(message = "La fecha de la excursión es obligatoria")
    @FutureOrPresent(message = "La fecha de la excursión no puede ser pasada")
    private LocalDate fechaExcursion;

    @NotNull(message = "La cantidad de personas es obligatoria")
    @Min(value = 1, message = "La cantidad de personas debe ser al menos 1")
    private Integer cantidadPersonas;

    @NotNull(message = "El origen del turista es obligatorio")
    @Valid
    private OrigenTuristaDTO origenTurista;
}
