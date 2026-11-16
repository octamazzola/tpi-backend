package ar.edu.utn.frc.bda.common.exceptions;

import ar.edu.utn.frc.bda.common.dtos.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones compartido por todos los microservicios Servlet (Apunte 17).
 * Se registra automáticamente vía {@code CommonWebAutoConfiguration}: alcanza con depender de common-lib.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponseDTO> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * Errores de Bean Validation (@Valid): devuelve el detalle por campo.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> validaciones = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> validaciones.put(err.getField(), err.getDefaultMessage()));

        ErrorResponseDTO error = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Error de Validación")
                .message("Uno o más campos enviados no cumplen con el formato requerido")
                .path(request.getRequestURI())
                .validaciones(validaciones)
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * JSON mal formado o con tipos incorrectos (ej: fecha con formato inválido).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido o tiene tipos incorrectos", request);
    }

    /**
     * Falla de comunicación con otro microservicio (caído, timeout o error 5xx).
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponseDTO> handleRestClient(RestClientException ex, HttpServletRequest request) {
        log.error("Error comunicándose con un servicio colaborador: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "Un servicio colaborador no está disponible. Intente nuevamente más tarde.", request);
    }

    /**
     * Parámetro de ruta o query con tipo incorrecto (ej: /reservas/abc o ?dificultad=FACIL).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El parámetro '" + ex.getName() + "' tiene un valor inválido: " + ex.getValue(), request);
    }

    /**
     * Excepciones estándar de Spring MVC que ya traen su código HTTP: ruta inexistente (404),
     * método no permitido (405), media type no soportado (415), parámetro faltante (400), etc.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponseDTO> handleSpringError(ErrorResponseException ex, HttpServletRequest request) {
        return buildSpring(ex, request);
    }

    /**
     * Cualquier error no contemplado: nunca se filtra el stacktrace al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse springError) {
            // HttpRequestMethodNotSupportedException, MissingServletRequestParameterException, etc.
            return buildSpring(springError, request);
        }
        log.error("Error interno no controlado en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno en el servidor", request);
    }

    private ResponseEntity<ErrorResponseDTO> buildSpring(ErrorResponse ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String detalle = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : status.getReasonPhrase();
        return build(status, detalle, request);
    }

    private ResponseEntity<ErrorResponseDTO> build(HttpStatus status, String mensaje, HttpServletRequest request) {
        ErrorResponseDTO error = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(mensaje)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(error);
    }
}
