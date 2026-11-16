package ar.edu.utn.frc.bda.common.config;

import ar.edu.utn.frc.bda.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frc.bda.common.logging.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuración de common-lib: en cualquier microservicio Servlet (Spring MVC) que tenga common-lib
 * en el classpath registra:
 * <ul>
 *   <li>{@link GlobalExceptionHandler}: formato de error uniforme.</li>
 *   <li>{@link CorrelationIdFilter}: Correlation ID en el MDC de cada petición (trazabilidad de logs).</li>
 * </ul>
 * No se activa en el api-gateway porque es una aplicación reactiva (WebFlux).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
public class CommonWebAutoConfiguration {
}
