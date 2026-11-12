package ms_reservas.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO de entrada para crear una reserva (POST /api/v1/reservas).
 * La cantidad de personas es la cantidad de participantes informados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaRequestDTO {

    // Solo se usa en pruebas locales sin gateway: vía gateway el turista sale del token JWT
    @Size(max = 100, message = "El identificador del turista no puede superar los 100 caracteres")
    private String turistaId;

    // Opcional: vía gateway el email sale del token JWT. Solo se usa en pruebas locales sin gateway
    // o si el usuario de Keycloak no tiene email cargado.
    @Email(message = "El email del turista no tiene un formato válido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String emailTurista;

    @NotNull(message = "La actividad es obligatoria")
    private Long actividadId;

    @NotNull(message = "La fecha de la excursión es obligatoria")
    @Future(message = "La fecha de la excursión debe ser posterior a hoy")
    private LocalDate fechaExcursion;

    @NotEmpty(message = "Debe informar al menos un participante")
    @Size(max = 50, message = "No se pueden reservar más de 50 participantes en una sola reserva")
    private List<@Valid ParticipanteDTO> participantes;

    @NotNull(message = "El origen del turista es obligatorio")
    @Valid
    private OrigenTuristaDTO origenTurista;
}
