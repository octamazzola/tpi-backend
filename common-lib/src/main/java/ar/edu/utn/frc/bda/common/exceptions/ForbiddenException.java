package ar.edu.utn.frc.bda.common.exceptions;

/**
 * Excepción de negocio compartida que se traduce a HTTP 403 Forbidden: el usuario intenta acceder a un recurso ajeno.
 * La conversión a respuesta HTTP la hace {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String mensaje) {
        super(mensaje);
    }
}
