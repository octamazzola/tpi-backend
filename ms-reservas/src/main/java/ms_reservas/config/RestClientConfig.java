package ms_reservas.config;

import ar.edu.utn.frc.bda.common.logging.CorrelationId;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Bean central de {@link RestClient} para las llamadas HTTP a catálogo, geoapi y Open-Meteo.
 * Define timeouts cortos para que una caída externa no bloquee la creación de reservas.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .requestFactory(factory)
                // Interceptor (Apunte 18) que propaga el Correlation ID (Apunte 24) a los servicios llamados,
                // para poder seguir una misma reserva en los logs de reservas, catálogo y geoapi.
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationId.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationId.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
