package ms_reservas.services;

import ms_reservas.entities.Reserva;

/**
 * Servicio de notificaciones por correo (SMTP hacia Mailpit).
 */
public interface EmailService {

    enum TipoNotificacion {
        /** F03: la reserva fue registrada (pendiente o rechazada por clima). */
        RESERVA_CREADA,
        /** F04: el operador confirmó la reserva; el mail funciona como voucher con el itinerario. */
        RESERVA_CONFIRMADA,
        /** F05 / revalidación: la reserva fue cancelada (por el turista, el admin o el sistema). */
        RESERVA_CANCELADA,
        /** Revalidación: el pronóstico cambió y la excursión ya no es viable. */
        RESERVA_RECHAZADA_POR_CLIMA,
        /** Regla de 48 hs: la reserva sigue pendiente y debe confirmarse o se anulará. */
        RECORDATORIO_CONFIRMACION
    }

    /**
     * Envía el correo de forma asincrónica. Un error de envío se registra en el log
     * y nunca interrumpe la operación de negocio.
     */
    void notificar(Reserva reserva, TipoNotificacion tipo);
}
