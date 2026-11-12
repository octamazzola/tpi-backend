package ar.edu.utn.frc.bda.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Metadatos OpenAPI comunes para los microservicios: título, versión y el esquema de seguridad
 * Bearer JWT (Keycloak), para que el botón "Authorize" de Swagger UI permita probar los endpoints
 * protegidos a través del api-gateway.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
public class CommonOpenApiAutoConfiguration {

    public static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI tpiOpenApi(@Value("${spring.application.name:microservicio}") String nombre) {
        return new OpenAPI()
                .info(new Info()
                        .title("TPI Backend - " + nombre)
                        .version("1.0.0")
                        .description("Sistema de Gestión de Actividades Turísticas y Excursiones (UTN FRC - Backend de Aplicaciones). "
                                + "Todas las llamadas pasan por el API Gateway; los endpoints protegidos requieren un token JWT de Keycloak."))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token obtenido de Keycloak (realm tpi-realm, cliente tpi-client)")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
