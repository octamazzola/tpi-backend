package ar.edu.utn.frc.bda.common.exceptions;

/**
 * Excepción de negocio compartida que se traduce a HTTP 404 Not Found: recurso inexistente (actividad, reserva, categoría).
 * La conversión a respuesta HTTP la hace {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }
}
