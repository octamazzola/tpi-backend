package api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuración de Seguridad Reactiva para Spring Cloud Gateway con Keycloak (OAuth2 Resource Server / JWT).
 * Implementa Role-Based Access Control (RBAC) granular extrayendo los roles de 'realm_access.roles'
 * y manejadores de error descriptivos en JSON para 401 Unauthorized y 403 Forbidden.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // Deshabilitamos CSRF porque trabajamos con tokens Bearer JWT stateless
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // =========================================================================
                        // 1. Catálogo - Endpoints Públicos (sin token)
                        // =========================================================================
                        .pathMatchers(HttpMethod.GET, "/api/v1/catalogo/**").permitAll()

                        // Métricas y Health check
                        .pathMatchers("/actuator/**").permitAll()

                        // =========================================================================
                        // 2. Catálogo - Modificaciones de Excursiones (Solo OPERADOR / ADMIN)
                        // =========================================================================
                        .pathMatchers(HttpMethod.POST, "/api/v1/catalogo/actividades").hasAnyRole("OPERADOR", "PROVEEDOR", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/catalogo/actividades/**").hasAnyRole("OPERADOR", "PROVEEDOR", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/catalogo/actividades/**").hasAnyRole("OPERADOR", "PROVEEDOR", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/catalogo/actividades/**").hasRole("ADMIN")

                        // Categorías maestras (Exclusivo ADMIN)
                        .pathMatchers(HttpMethod.POST, "/api/v1/catalogo/categorias").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/catalogo/categorias/**").hasRole("ADMIN")

                        // =========================================================================
                        // 3. Reservas (RBAC Granular)
                        // =========================================================================
                        .pathMatchers(HttpMethod.POST, "/api/v1/reservas").hasAnyRole("TURISTA", "ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/reservas/mis-reservas").hasAnyRole("TURISTA", "ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/reservas/actividad/**").hasAnyRole("OPERADOR", "PROVEEDOR", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/reservas/*/confirmar").hasAnyRole("OPERADOR", "PROVEEDOR", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/reservas/*/cancelar").hasAnyRole("TURISTA", "ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/reservas").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/reservas/**").authenticated()

                        // =========================================================================
                        // 4. GeoAPI / OSRM
                        // =========================================================================
                        .pathMatchers(HttpMethod.GET, "/api/v1/geo/health-osrm").hasRole("ADMIN")
                        .pathMatchers("/api/v1/geo/**").authenticated()

                        // Cualquier otra petición debe estar autenticada
                        .anyExchange().authenticated()
                )
                // Manejo de errores de seguridad personalizados con cuerpo JSON descriptivo
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )
                // Habilitamos validación de tokens JWT contra Keycloak y aplicamos extractor de roles
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor()))
                )
                .build();
    }

    /**
     * Respuesta personalizada en JSON para errores 401 Unauthorized (sin token o token inválido).
     */
    private ServerAuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (exchange, authException) -> {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            String path = exchange.getRequest().getPath().value();
            String json = String.format(
                    "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"No se encuentra autenticado. Debe proporcionar un token Bearer JWT válido en el encabezado Authorization.\",\"path\":\"%s\"}",
                    LocalDateTime.now(), path
            );

            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        };
    }

    /**
     * Respuesta personalizada en JSON para errores 403 Forbidden (rol insuficiente).
     */
    private ServerAccessDeniedHandler customAccessDeniedHandler() {
        return (exchange, deniedException) -> {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            String path = exchange.getRequest().getPath().value();
            String json = String.format(
                    "{\"timestamp\":\"%s\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Acceso denegado: su rol actual no cuenta con los permisos necesarios para realizar esta operación.\",\"path\":\"%s\"}",
                    LocalDateTime.now(), path
            );

            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        };
    }

    /**
     * Extrae los roles del claim 'realm_access.roles' que genera Keycloak en el token JWT
     * y los mapea a GrantedAuthorities de Spring Security con prefijo 'ROLE_'.
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        return jwt -> {
            Collection<GrantedAuthority> authorities = extractRealmRoles(jwt);
            return Mono.just(new JwtAuthenticationToken(jwt, authorities));
        };
    }

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || realmAccess.isEmpty()) {
            return Collections.emptyList();
        }

        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return Collections.emptyList();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Object role : roles) {
            if (role instanceof String roleName) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                // Sinónimos para conveniencia entre OPERADOR y PROVEEDOR
                if ("OPERADOR".equalsIgnoreCase(roleName)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_PROVEEDOR"));
                } else if ("PROVEEDOR".equalsIgnoreCase(roleName)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_OPERADOR"));
                }
            }
        }
        return authorities;
    }
}
