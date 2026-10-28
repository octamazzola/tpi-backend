package ms_reservas.services;

import ms_reservas.entities.Reserva;

/**
 * Servicio de notificaciones por correo (SMTP hacia Mailpit).
 */
public interface EmailService {

    void enviarConfirmacionReserva(Reserva reserva);
}
