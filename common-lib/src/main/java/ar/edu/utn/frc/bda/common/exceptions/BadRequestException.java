package ar.edu.utn.frc.bda.common.exceptions;

/**
 * Excepción de negocio compartida que se traduce a HTTP 400 Bad Request: regla de negocio o dato inválido.
 * La conversión a respuesta HTTP la hace {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String mensaje) {
        super(mensaje);
    }
}
