package api_gateway.config;

import ar.edu.utn.frc.bda.common.logging.CorrelationId;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generaUnIdLoReenviaYLoDevuelveAlCliente() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/catalogo/actividades"));

        HttpHeaders reenviados = ejecutar(comoNetty(exchange));

        String id = reenviados.getFirst(CorrelationId.HEADER);
        assertThat(id).isNotBlank();
        // El microservicio también devuelve el header: en la respuesta final tiene que quedar uno solo
        assertThat(exchange.getResponse().getHeaders().get(CorrelationId.HEADER)).containsExactly(id);
    }

    @Test
    void respetaElIdQueMandaElCliente() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/reservas").header(CorrelationId.HEADER, "demo-1"));

        HttpHeaders reenviados = ejecutar(comoNetty(exchange));

        assertThat(reenviados.getFirst(CorrelationId.HEADER)).isEqualTo("demo-1");
    }

    /** Como en producción (Netty), los headers de la petición entrante son de solo lectura. */
    private static ServerWebExchange comoNetty(MockServerWebExchange exchange) {
        HttpHeaders soloLectura = HttpHeaders.readOnlyHttpHeaders(exchange.getRequest().getHeaders());
        return exchange.mutate().request(new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                return soloLectura;
            }
        }).build();
    }

    private HttpHeaders ejecutar(ServerWebExchange exchange) {
        AtomicReference<HttpHeaders> capturados = new AtomicReference<>();
        // Simula al microservicio: devuelve el mismo header (como hace el filtro de common-lib) y responde
        GatewayFilterChain chain = ex -> {
            capturados.set(ex.getRequest().getHeaders());
            ex.getResponse().getHeaders().add(CorrelationId.HEADER, ex.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
            return ex.getResponse().setComplete();
        };
        filter.filter(exchange, chain).block();
        return capturados.get();
    }
}
