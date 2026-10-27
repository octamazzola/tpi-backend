package api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración reactiva de rutas para Spring Cloud Gateway (conforme a los lineamientos del Apunte 19).
 */
@Configuration
public class GatewayConfig {

    @Value("${tpi.gateway.ms-catalogo-url}")
    private String msCatalogoUrl;

    @Value("${tpi.gateway.ms-reservas-url}")
    private String msReservasUrl;

    @Value("${tpi.gateway.geoapi-url}")
    private String geoapiUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 1. Ruteo hacia el Microservicio de Catálogo
                .route("ms-catalogo", r -> r
                        .path("/api/v1/catalogo/**")
                        .uri(msCatalogoUrl))

                // 2. Ruteo hacia el Microservicio de Reservas
                .route("ms-reservas", r -> r
                        .path("/api/v1/reservas/**")
                        .uri(msReservasUrl))

                // 3. Ruteo hacia el Microservicio Geoespacial (OSRM)
                .route("geoapi", r -> r
                        .path("/api/v1/geo/**")
                        .uri(geoapiUrl))
                .build();
    }
}
