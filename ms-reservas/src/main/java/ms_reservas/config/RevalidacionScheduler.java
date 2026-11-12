package ms_reservas.config;

import lombok.RequiredArgsConstructor;
import ms_reservas.services.RevalidacionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        revalidacionService.revalidar();
    }
}
