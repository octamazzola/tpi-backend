package ms_reservas.services;

import ar.edu.utn.frc.bda.common.security.UsuarioAutenticado;
import ms_reservas.dtos.request.ReservaRequestDTO;
import ms_reservas.dtos.response.ReservaResponseDTO;

import java.util.List;

/**
 * Lógica de negocio de reservas: orquesta catálogo, clima, geoapi y notificaciones.
 *
 * El parámetro {@code usuario} es el usuario propagado por el api-gateway; puede ser null cuando
 * la petición llega directo al microservicio (pruebas locales sin gateway).
 */
public interface ReservaService {

    ReservaResponseDTO crear(ReservaRequestDTO dto, UsuarioAutenticado usuario);

    List<ReservaResponseDTO> listarTodas();

    List<ReservaResponseDTO> listarPorTurista(String turistaId);

    ReservaResponseDTO obtenerPorId(Long id, UsuarioAutenticado usuario);

    List<ReservaResponseDTO> listarPorActividad(Long actividadId);

    ReservaResponseDTO confirmar(Long id);

    ReservaResponseDTO cancelar(Long id, UsuarioAutenticado usuario);
}
