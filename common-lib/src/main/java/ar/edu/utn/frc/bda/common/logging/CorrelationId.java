package ar.edu.utn.frc.bda.common.logging;

/**
 * Contrato del Correlation ID (Apunte 23 y 24): un identificador por petición que viaja en el header
 * {@code X-Correlation-ID} entre el gateway y los microservicios, y se guarda en el MDC del log con la
 * clave {@code correlationId}. Así se pueden seguir en los logs todos los mensajes de una misma operación.
 */
public final class CorrelationId {

    /** Header HTTP que transporta el identificador entre servicios. */
    public static final String HEADER = "X-Correlation-ID";

    /** Clave del MDC (la usa el patrón de log: %X{correlationId}). */
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }
}
