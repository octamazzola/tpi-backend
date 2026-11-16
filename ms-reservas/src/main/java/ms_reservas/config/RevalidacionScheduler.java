package ms_reservas.config;

import ar.edu.utn.frc.bda.common.logging.CorrelationId;
import lombok.RequiredArgsConstructor;
import ms_reservas.services.RevalidacionService;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ejecuta la revalidación de reservas de forma periódica (por defecto, cada hora en punto).
 * Se configura con reservas.revalidacion.cron y se puede apagar con reservas.revalidacion.habilitada=false.
 * El ADMIN también puede dispararla a mano con POST /api/v1/reservas/revalidaciones.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservas.revalidacion.habilitada", havingValue = "true", matchIfMissing = true)
public class RevalidacionScheduler {

    private final RevalidacionService revalidacionService;

    @Scheduled(cron = "${reservas.revalidacion.cron:0 0 * * * *}", zone = "America/Argentina/Cordoba")
    public void ejecutar() {
        // No hay petición HTTP que traiga un Correlation ID: cada ejecución programada genera el suyo
        MDC.put(CorrelationId.MDC_KEY, "revalidacion-" + UUID.randomUUID());
        try {
            revalidacionService.revalidar();
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
