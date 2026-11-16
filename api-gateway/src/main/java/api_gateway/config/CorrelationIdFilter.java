package api_gateway.config;

import ar.edu.utn.frc.bda.common.logging.CorrelationId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Punto de origen del Correlation ID (Apunte 24): si la petición no trae {@code X-Correlation-ID}, el
 * gateway genera uno. Lo reenvía al microservicio, lo devuelve en la respuesta al cliente y deja una línea
 * de log por petición. Con ese mismo identificador se buscan después los logs de cada servicio.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest original = exchange.getRequest();
        String recibido = original.getHeaders().getFirst(CorrelationId.HEADER);
        String correlationId = (recibido == null || recibido.isBlank()) ? UUID.randomUUID().toString() : recibido;

        // Los headers de la petición entrante son de solo lectura (Netty): se arma una copia
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(original.getHeaders());
        headers.set(CorrelationId.HEADER, correlationId);
        HttpHeaders soloLectura = HttpHeaders.readOnlyHttpHeaders(headers);
        ServerHttpRequest conId = new ServerHttpRequestDecorator(original) {
            @Override
            public HttpHeaders getHeaders() {
                return soloLectura;
            }
        };

        // Se fija justo antes de enviar la respuesta con set (no add): el microservicio también devuelve
        // el header y el gateway lo copiaría, quedando duplicado.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
            return Mono.empty();
        });
        log.info("[{}] {} {}", correlationId, original.getMethod(), original.getPath().value());
        return chain.filter(exchange.mutate().request(conId).build());
    }

    @Override
    public int getOrder() {
        // Antes que UserHeadersFilter: así la identidad se arma sobre la petición que ya tiene el ID
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
