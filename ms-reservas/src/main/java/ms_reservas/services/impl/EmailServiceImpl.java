package ms_reservas.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.entities.Reserva;
import ms_reservas.services.EmailService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void enviarConfirmacionReserva(Reserva reserva) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(reserva.getEmailTurista());
            message.setSubject("Confirmación de Reserva #" + reserva.getId() + " - " + reserva.getTituloActividad());
            message.setText("¡Hola! Tu reserva ha sido " + reserva.getEstado() + ".\n"
                    + "Fecha de la excursión: " + reserva.getFechaExcursion() + "\n"
                    + "Personas: " + reserva.getCantidadPersonas() + "\n"
                    + "Monto Total: $" + reserva.getMontoTotal() + "\n"
                    + "Tiempo de traslado estimado: " + reserva.getDuracionEstimadaViaje() + " (" + reserva.getDistanciaKm() + " km)\n");
            mailSender.send(message);
        } catch (Exception e) {
            // Loguear error pero nunca interrumpir la transacción de la reserva
            log.error("No se pudo enviar el email de la reserva #{}: {}", reserva.getId(), e.getMessage());
        }
    }
}
