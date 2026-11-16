package ms_reservas.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.entities.Participante;
import ms_reservas.entities.Reserva;
import ms_reservas.services.EmailService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private static final String REMITENTE = "reservas@turismo-cordoba.local";

    private final JavaMailSender mailSender;

    @Override
    @Async
    public void notificar(Reserva reserva, TipoNotificacion tipo) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(REMITENTE);
            message.setTo(reserva.getEmailTurista());
            message.setSubject(asunto(reserva, tipo));
            message.setText(cuerpo(reserva, tipo));
            mailSender.send(message);
            log.info("Email {} enviado para la reserva #{}", tipo, reserva.getId());
        } catch (Exception e) {
            // Loguear el error pero nunca interrumpir la operación de negocio
            log.error("No se pudo enviar el email {} de la reserva #{}: {}", tipo, reserva.getId(), e.getMessage());
        }
    }

    private String asunto(Reserva r, TipoNotificacion tipo) {
        String base = " - Reserva #" + r.getId() + " - " + r.getTituloActividad();
        return switch (tipo) {
            case RESERVA_CREADA -> "Recibimos tu solicitud" + base;
            case RESERVA_CONFIRMADA -> "Voucher: reserva confirmada" + base;
            case RESERVA_CANCELADA -> "Reserva cancelada" + base;
            case RESERVA_RECHAZADA_POR_CLIMA -> "Excursión suspendida por clima" + base;
            case RECORDATORIO_CONFIRMACION -> "Recordatorio: tu reserva sigue pendiente" + base;
        };
    }

    private String cuerpo(Reserva r, TipoNotificacion tipo) {
        String intro = switch (tipo) {
            case RESERVA_CREADA -> "¡Hola! Registramos tu reserva. Estado actual: " + r.getEstado() + ".";
            case RESERVA_CONFIRMADA -> "¡Tu reserva está confirmada! Presentá este correo como voucher en el punto de encuentro.";
            case RESERVA_CANCELADA -> "Tu reserva fue cancelada y los lugares quedaron liberados.";
            case RESERVA_RECHAZADA_POR_CLIMA -> "Lamentablemente el pronóstico no permite realizar la excursión en la fecha elegida.";
            case RECORDATORIO_CONFIRMACION -> "Faltan menos de 48 hs para tu excursión y la reserva sigue PENDIENTE. "
                    + "Comunicate con el prestador para confirmarla: si no se confirma 24 hs antes, se anulará automáticamente.";
        };

        String participantes = r.getParticipantes().stream()
                .map(Participante::getNombre)
                .collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder(intro).append("\n\n")
                .append("Actividad: ").append(r.getTituloActividad()).append('\n')
                .append("Fecha de la excursión: ").append(r.getFechaExcursion()).append('\n')
                .append("Participantes (").append(r.getCantidadPersonas()).append("): ").append(participantes).append('\n')
                .append("Monto total: $").append(r.getMontoTotal()).append('\n')
                .append("Traslado estimado: ").append(r.getDuracionEstimadaViaje());
        if (r.getDistanciaKm() != null) {
            sb.append(" (").append(r.getDistanciaKm()).append(" km)");
        }
        sb.append('\n');
        if (r.getDescripcionClima() != null) {
            sb.append("Clima: ").append(r.getDescripcionClima()).append('\n');
        }
        if (r.getMotivoEstado() != null) {
            sb.append("Motivo: ").append(r.getMotivoEstado()).append('\n');
        }
        return sb.toString();
    }
}
