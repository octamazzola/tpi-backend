package ms_reservas.dtos.response;

import lombok.*;
import ms_reservas.dtos.request.ParticipanteDTO;
import ms_reservas.entities.EstadoReserva;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de salida enriquecido de una reserva.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaResponseDTO {

    private Long id;
    private String turistaId;
    private String emailTurista;
    private Long actividadId;
    private String tituloActividad;
    private LocalDate fechaExcursion;
    private Integer cantidadPersonas;
    private List<ParticipanteDTO> participantes;
    private Double montoTotal;
    private EstadoReserva estado;
    private String motivoEstado;
    private TrasladoEstimadoDTO trasladoEstimado;
    private Boolean alertaClimatica;
    private String descripcionClima;
    private LocalDateTime fechaCreacion;
}
