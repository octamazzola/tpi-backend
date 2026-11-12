package ar.edu.utn.frc.bda.common.config;

import ar.edu.utn.frc.bda.common.exceptions.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuración de common-lib: registra el {@link GlobalExceptionHandler} en cualquier
 * microservicio Servlet (Spring MVC) que tenga common-lib en el classpath.
 * No se activa en el api-gateway porque es una aplicación reactiva (WebFlux).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(GlobalExceptionHandler.class)
public class CommonWebAutoConfiguration {
}
