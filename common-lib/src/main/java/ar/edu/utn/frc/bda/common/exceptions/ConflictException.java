package ar.edu.utn.frc.bda.common.exceptions;

/**
 * Excepción de negocio compartida que se traduce a HTTP 409 Conflict: conflicto de negocio (cupo agotado, categoría con actividades).
 * La conversión a respuesta HTTP la hace {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String mensaje) {
        super(mensaje);
    }
}
