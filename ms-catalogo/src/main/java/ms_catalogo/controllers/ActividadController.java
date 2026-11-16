package ms_catalogo.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ms_catalogo.dtos.ActividadPatchDTO;
import ms_catalogo.dtos.ActividadRequestDTO;
import ms_catalogo.dtos.ActividadResponseDTO;
import ms_catalogo.dtos.DisponibilidadDTO;
import ms_catalogo.entities.Dificultad;
import ms_catalogo.services.ActividadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de Actividades y Excursiones turísticas.
 *
 * Expone los endpoints principales consumidos tanto por los clientes (a través del Gateway)
 * como por otros microservicios (ms-reservas consulta el detalle de la actividad).
 */
@RestController
@RequestMapping("/api/v1/catalogo/actividades")
@RequiredArgsConstructor
@Tag(name = "Actividades", description = "Oferta de excursiones: consulta pública y gestión por prestadores")
public class ActividadController {

    private final ActividadService actividadService;

    @GetMapping
    @Operation(summary = "Listar actividades activas (público)",
            description = "Filtros opcionales combinables. La ciudad es una búsqueda parcial que no distingue "
                    + "mayúsculas ni tildes. Sin coincidencias responde 200 con una lista vacía.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado (puede estar vacío)"),
            @ApiResponse(responseCode = "400", description = "Parámetro con valor inválido (ej. dificultad inexistente)")
    })
    public ResponseEntity<List<ActividadResponseDTO>> listar(
            @Parameter(description = "ID de categoría") @RequestParam(required = false) Long categoriaId,
            @Parameter(description = "BAJA, MEDIA o ALTA") @RequestParam(required = false) Dificultad dificultad,
            @Parameter(description = "Precio base máximo") @RequestParam(required = false) Double precioMax,
            @Parameter(description = "Ciudad del punto de encuentro (parcial)", example = "carlos paz")
            @RequestParam(required = false) String ciudad) {
        return ResponseEntity.ok(actividadService.listarActividades(categoriaId, dificultad, precioMax, ciudad));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una actividad (público)",
            description = "Detalle con punto de encuentro, cupo, restricciones de edad, temporada y dependencia del clima. "
                    + "Lo consume ms-reservas al crear y revalidar reservas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actividad encontrada"),
            @ApiResponse(responseCode = "404", description = "No existe o fue dada de baja")
    })
    public ResponseEntity<ActividadResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(actividadService.obtenerPorId(id));
    }

    @GetMapping("/{id}/disponibilidad")
    @Operation(summary = "Consultar si la actividad está activa y tiene cupo definido (público)",
            description = "Indica si la actividad puede recibir reservas. La ocupación por fecha la calcula ms-reservas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de la actividad"),
            @ApiResponse(responseCode = "404", description = "No existe o fue dada de baja")
    })
    public ResponseEntity<DisponibilidadDTO> consultarDisponibilidad(@PathVariable Long id) {
        return ResponseEntity.ok(actividadService.consultarDisponibilidad(id));
    }

    @PostMapping
    @Operation(summary = "Publicar una actividad (OPERADOR, ADMIN)",
            description = "Alta con ubicación, cupo, precio, ciudad y restricciones. Queda activa de inmediato.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Actividad creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos"),
            @ApiResponse(responseCode = "404", description = "La categoría no existe"),
            @ApiResponse(responseCode = "422", description = "Edad mínima mayor que la máxima o temporada incompleta")
    })
    public ResponseEntity<ActividadResponseDTO> crear(@Valid @RequestBody ActividadRequestDTO dto) {
        ActividadResponseDTO creada = actividadService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reemplazar una actividad (OPERADOR, ADMIN)", description = "Redefine todas sus condiciones.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actividad actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos"),
            @ApiResponse(responseCode = "404", description = "La actividad o la categoría no existen"),
            @ApiResponse(responseCode = "422", description = "Edad mínima mayor que la máxima o temporada incompleta")
    })
    public ResponseEntity<ActividadResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActividadRequestDTO dto) {
        return ResponseEntity.ok(actividadService.actualizar(id, dto));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Ajustar una actividad (OPERADOR, ADMIN)",
            description = "Modifica solo los campos enviados (ej. precio o cupo).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actividad actualizada"),
            @ApiResponse(responseCode = "400", description = "Algún campo tiene un valor inválido"),
            @ApiResponse(responseCode = "404", description = "La actividad o la categoría no existen"),
            @ApiResponse(responseCode = "422", description = "La combinación resultante no es válida (edad o temporada)")
    })
    public ResponseEntity<ActividadResponseDTO> actualizarParcial(
            @PathVariable Long id,
            @Valid @RequestBody ActividadPatchDTO dto) {
        return ResponseEntity.ok(actividadService.actualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Dar de baja una actividad (ADMIN)",
            description = "Baja lógica (activa = false): no se borra para preservar el historial de reservas. "
                    + "La revalidación de ms-reservas cancela las reservas futuras.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Actividad dada de baja"),
            @ApiResponse(responseCode = "404", description = "No existe o ya estaba dada de baja")
    })
    public ResponseEntity<Void> darDeBaja(@PathVariable Long id) {
        actividadService.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }
}
