package api_gateway.config;

import ar.edu.utn.frc.bda.common.security.UserHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Filtro global que propaga la identidad del usuario autenticado hacia los microservicios internos.
 *
 * 1. Elimina SIEMPRE los headers X-User-* que envíe el cliente (evita falsificar la identidad).
 * 2. Si la petición trae un JWT válido de Keycloak, agrega X-User-Id (sub), X-User-Name
 *    (preferred_username), X-User-Email (email) y X-User-Roles (realm roles separados por coma).
 *
 * Así ms-reservas sabe qué turista hace la operación sin confiar en el cuerpo JSON.
 */
@Component
public class UserHeadersFilter implements GlobalFilter, Ordered {

    private static final List<String> HEADERS_IDENTIDAD =
            List.of(UserHeaders.USER_ID, UserHeaders.USERNAME, UserHeaders.EMAIL, UserHeaders.ROLES);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(auth -> chain.filter(exchange.mutate()
                        .request(conIdentidad(exchange.getRequest(), auth))
                        .build()));
    }

    /**
     * Los headers de la petición original son de solo lectura (Netty), así que se arma una copia
     * sin los X-User-* entrantes, se agregan los del token y se expone mediante un decorador.
     */
    private ServerHttpRequest conIdentidad(ServerHttpRequest original, Optional<JwtAuthenticationToken> auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(original.getHeaders());
        HEADERS_IDENTIDAD.forEach(headers::remove);
        auth.ifPresent(a -> agregarIdentidad(headers, a));

        HttpHeaders soloLectura = HttpHeaders.readOnlyHttpHeaders(headers);
        return new ServerHttpRequestDecorator(original) {
            @Override
            public HttpHeaders getHeaders() {
                return soloLectura;
            }
        };
    }

    private void agregarIdentidad(HttpHeaders headers, JwtAuthenticationToken auth) {
        Jwt jwt = auth.getToken();
        setSiPresente(headers, UserHeaders.USER_ID, jwt.getSubject());
        setSiPresente(headers, UserHeaders.USERNAME, jwt.getClaimAsString("preferred_username"));
        setSiPresente(headers, UserHeaders.EMAIL, jwt.getClaimAsString("email"));

        // Las authorities ya vienen mapeadas por SecurityConfig como ROLE_<rol>
        String roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .distinct()
                .collect(Collectors.joining(","));
        setSiPresente(headers, UserHeaders.ROLES, roles);
    }

    private void setSiPresente(HttpHeaders headers, String nombre, String valor) {
        if (valor != null && !valor.isBlank()) {
            headers.set(nombre, valor);
        }
    }

    @Override
    public int getOrder() {
        // Antes de los filtros de ruteo que reenvían la petición al microservicio
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
