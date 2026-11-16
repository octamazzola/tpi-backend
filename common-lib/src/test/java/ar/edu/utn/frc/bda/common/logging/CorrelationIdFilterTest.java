package ar.edu.utn.frc.bda.common.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void usaElIdRecibidoLoPoneEnElMdcYLoDevuelve() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reservas");
        request.addHeader(CorrelationId.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> enMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> enMdc.set(MDC.get(CorrelationId.MDC_KEY)));

        assertThat(enMdc.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("abc-123");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).as("se limpia al terminar").isNull();
    }

    @Test
    void generaUnIdSiLaPeticionNoTrae() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/x"), response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotBlank();
    }
}
