package ms_reservas.dtos.response;

import ms_reservas.entities.EstadoReserva;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resumen de una ejecución de la revalidación de reservas.
 *
 * @param reservasRevisadas        reservas PENDIENTE/CONFIRMADA con fecha futura que se evaluaron
 * @param actividadesNoVerificadas actividades que no se pudieron consultar porque catálogo no respondió
 *                                 (sus reservas se dejan como están y se reintenta en la próxima ejecución)
 * @param recordatoriosEnviados    emails de la regla de 48 hs enviados en esta ejecución
 * @param cambios                  reservas que cambiaron de estado y por qué
 */
public record ResultadoRevalidacionDTO(
        LocalDateTime ejecutadaEn,
        int reservasRevisadas,
        int actividadesNoVerificadas,
        int recordatoriosEnviados,
        List<CambioEstadoDTO> cambios
) {

    public record CambioEstadoDTO(
            Long reservaId,
            Long actividadId,
            LocalDate fechaExcursion,
            EstadoReserva estadoAnterior,
            EstadoReserva estadoNuevo,
            String motivo
    ) {}
}
