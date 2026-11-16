package ms_reservas.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Los emails se envían con @Async en otro hilo, y el MDC (donde vive el Correlation ID) es por hilo.
 * Este decorador copia el MDC del hilo que pide el envío al hilo que lo ejecuta, para que los logs del
 * mail conserven el mismo Correlation ID que la reserva. Spring Boot lo aplica solo al executor de @Async.
 */
@Configuration
public class AsyncConfig {

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return tarea -> {
            Map<String, String> contexto = MDC.getCopyOfContextMap();
            return () -> {
                if (contexto != null) {
                    MDC.setContextMap(contexto);
                }
                try {
                    tarea.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
