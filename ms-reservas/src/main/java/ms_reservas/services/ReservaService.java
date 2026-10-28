package ms_reservas.services;

import ms_reservas.dtos.request.ReservaRequestDTO;
import ms_reservas.dtos.response.ReservaResponseDTO;

import java.util.List;

/**
 * Lógica de negocio de reservas: orquesta catálogo, clima, geoapi y notificaciones.
 */
public interface ReservaService {

    ReservaResponseDTO crear(ReservaRequestDTO dto);

    List<ReservaResponseDTO> listarPorTurista(String turistaId);

    ReservaResponseDTO obtenerPorId(Long id);

    List<ReservaResponseDTO> listarPorActividad(Long actividadId);

    ReservaResponseDTO confirmar(Long id);

    ReservaResponseDTO cancelar(Long id);
}
