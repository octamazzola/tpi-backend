package ar.edu.utn.frc.bda.common.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Estructura unificada de error para todos los microservicios:
 * { "timestamp", "status", "error", "message", "path", "validaciones" }.
 * Usa los mismos nombres de campo que las respuestas 401/403 del api-gateway y que geoapi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDTO {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    // Errores de Bean Validation por campo (ej: "titulo": "no puede estar vacío")
    private Map<String, String> validaciones;
}
