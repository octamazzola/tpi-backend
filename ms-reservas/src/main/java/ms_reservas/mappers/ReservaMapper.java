package ms_reservas.mappers;

import ms_reservas.dtos.request.ParticipanteDTO;
import ms_reservas.dtos.response.ReservaResponseDTO;
import ms_reservas.dtos.response.TrasladoEstimadoDTO;
import ms_reservas.entities.Participante;
import ms_reservas.entities.Reserva;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce entre la entidad Reserva y sus DTOs.
 */
@Component
public class ReservaMapper {

    public List<Participante> toParticipantes(List<ParticipanteDTO> dtos) {
        return dtos.stream()
                .map(p -> Participante.builder()
                        .nombre(p.getNombre().trim())
                        .dni(p.getDni())
                        .fechaNacimiento(p.getFechaNacimiento())
                        .telefono(p.getTelefono())
                        .build())
                .toList();
    }

    public ReservaResponseDTO toResponseDTO(Reserva r) {
        return ReservaResponseDTO.builder()
                .id(r.getId())
                .turistaId(r.getTuristaId())
                .emailTurista(r.getEmailTurista())
                .actividadId(r.getActividadId())
                .tituloActividad(r.getTituloActividad())
                .fechaExcursion(r.getFechaExcursion())
                .cantidadPersonas(r.getCantidadPersonas())
                .participantes(r.getParticipantes().stream()
                        .map(p -> ParticipanteDTO.builder()
                                .nombre(p.getNombre())
                                .dni(p.getDni())
                                .fechaNacimiento(p.getFechaNacimiento())
                                .telefono(p.getTelefono())
                                .build())
                        .toList())
                .montoTotal(r.getMontoTotal())
                .estado(r.getEstado())
                .motivoEstado(r.getMotivoEstado())
                .trasladoEstimado(TrasladoEstimadoDTO.builder()
                        .distanciaKm(r.getDistanciaKm())
                        .duracionFormateada(r.getDuracionEstimadaViaje())
                        .build())
                .alertaClimatica(r.getAlertaClimatica())
                .descripcionClima(r.getDescripcionClima())
                .fechaCreacion(r.getFechaCreacion())
                .build();
    }
}
