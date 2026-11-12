package api_gateway.config;

import ar.edu.utn.frc.bda.common.security.UserHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserHeadersFilterTest {

    private final UserHeadersFilter filter = new UserHeadersFilter();

    @Test
    void propagaIdentidadDelJwtYPisaHeadersFalsificados() {
        ServerWebExchange exchange = comoNetty(MockServerHttpRequest
                .post("/api/v1/reservas")
                .header(UserHeaders.USERNAME, "turista2")       // intento de suplantación
                .header(UserHeaders.ROLES, "ADMIN"));

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("uuid-123")
                .claim("preferred_username", "turista1")
                .claim("email", "turista1@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_TURISTA")));

        HttpHeaders reenviados = ejecutar(exchange, auth);

        assertThat(reenviados.getFirst(UserHeaders.USER_ID)).isEqualTo("uuid-123");
        assertThat(reenviados.get(UserHeaders.USERNAME)).containsExactly("turista1");
        assertThat(reenviados.getFirst(UserHeaders.EMAIL)).isEqualTo("turista1@example.com");
        assertThat(reenviados.get(UserHeaders.ROLES)).containsExactly("TURISTA");
    }

    @Test
    void sinAutenticacionEliminaLosHeadersDeIdentidadYConservaElResto() {
        ServerWebExchange exchange = comoNetty(MockServerHttpRequest
                .get("/api/v1/catalogo/actividades")
                .header(UserHeaders.USERNAME, "admin1")
                .header(UserHeaders.ROLES, "ADMIN")
                .header("Accept", "application/json"));

        HttpHeaders reenviados = ejecutar(exchange, null);

        assertThat(reenviados.containsKey(UserHeaders.USERNAME)).isFalse();
        assertThat(reenviados.containsKey(UserHeaders.ROLES)).isFalse();
        assertThat(reenviados.getFirst("Accept")).isEqualTo("application/json");
    }

    /**
     * Igual que Netty en producción: los headers de la petición entrante son de solo lectura.
     * La primera versión del filtro intentaba modificarlos y fallaba con 500 en ejecución real.
     */
    private static ServerWebExchange comoNetty(MockServerHttpRequest.BaseBuilder<?> builder) {
        MockServerHttpRequest request = builder.build();
        HttpHeaders soloLectura = HttpHeaders.readOnlyHttpHeaders(request.getHeaders());
        return MockServerWebExchange.from(request).mutate()
                .request(new ServerHttpRequestDecorator(request) {
                    @Override
                    public HttpHeaders getHeaders() {
                        return soloLectura;
                    }
                })
                .build();
    }

    private HttpHeaders ejecutar(ServerWebExchange exchange, JwtAuthenticationToken auth) {
        AtomicReference<HttpHeaders> capturados = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturados.set(ex.getRequest().getHeaders());
            return Mono.empty();
        };
        Mono<Void> resultado = filter.filter(exchange, chain);
        if (auth != null) {
            resultado = resultado.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        }
        resultado.block();
        return capturados.get();
    }
}
