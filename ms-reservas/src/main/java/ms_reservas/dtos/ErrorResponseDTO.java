package ms_reservas.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO estándar para respuestas de error de la API (conforme a las pautas de cátedra).
 * Provee una estructura unificada y predecible para el frontend y clientes HTTP.
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

    // Contiene los errores de validación de Bean Validation por campo
    private Map<String, String> validaciones;
}
