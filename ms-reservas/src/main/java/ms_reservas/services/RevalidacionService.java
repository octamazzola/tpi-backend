package ms_reservas.services;

import ms_reservas.dtos.response.ResultadoRevalidacionDTO;

/**
 * Revalida las reservas vigentes ante cambios posteriores a su creación
 * ("una reserva inicialmente válida podría dejar de ser viable").
 */
public interface RevalidacionService {

    ResultadoRevalidacionDTO revalidar();
}
