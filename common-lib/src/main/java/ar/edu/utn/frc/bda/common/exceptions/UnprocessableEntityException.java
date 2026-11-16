package ar.edu.utn.frc.bda.common.exceptions;

/**
 * Excepción de negocio compartida que se traduce a HTTP 422 Unprocessable Entity.
 * Según el Apunte 14: los datos tienen el formato correcto pero no cumplen las reglas de negocio de la
 * acción pedida (ej. un participante sin la edad mínima, una fecha fuera de temporada).
 * El 400 queda para datos mal formados o incompletos y el 409 para conflictos con el estado actual.
 */
public class UnprocessableEntityException extends RuntimeException {

    public UnprocessableEntityException(String mensaje) {
        super(mensaje);
    }
}
