package ms_catalogo.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ms_catalogo.dtos.CategoriaRequestDTO;
import ms_catalogo.dtos.CategoriaResponseDTO;
import ms_catalogo.services.CategoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * Controlador REST para la gestión de Categorías de excursiones.
 * 
 * @RestController: Le indica a Spring que esta clase atiende peticiones HTTP y 
 *                  serializa las respuestas automáticamente a formato JSON.
 * @RequestMapping: Prefijo de ruta base para todos los endpoints de este controlador.
 * @RequiredArgsConstructor: Genera el constructor con 'private final CategoriaService',
 *                           permitiendo la Inyección de Dependencias por constructor (buena práctica).
 */
@RestController
@RequestMapping("/api/v1/catalogo/categorias")
@RequiredArgsConstructor
@Tag(name = "Categorías", description = "Clasificación de las excursiones (gestión exclusiva del ADMIN)")
public class CategoriaController {

    private final CategoriaService categoriaService;

    /**
     * GET /api/v1/catalogo/categorias
     * Retorna el listado completo de categorías disponibles en el sistema.
     * Retorna HTTP 200 OK.
     */
    @GetMapping
    @Operation(summary = "Listar categorías (público)")
    @ApiResponse(responseCode = "200", description = "Listado de categorías")
    public ResponseEntity<List<CategoriaResponseDTO>> obtenerTodas() {
        return ResponseEntity.ok(categoriaService.obtenerTodas());
    }

    /**
     * GET /api/v1/catalogo/categorias/{id}
     * Busca una categoría específica por su clave primaria.
     * Si no existe, el Service lanzará ResourceNotFoundException (HTTP 404).
     */
    @GetMapping("/{id}")
    @Operation(summary = "Consultar una categoría (público)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría encontrada"),
            @ApiResponse(responseCode = "404", description = "La categoría no existe")
    })
    public ResponseEntity<CategoriaResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(categoriaService.obtenerPorId(id));
    }

    /**
     * POST /api/v1/catalogo/categorias
     * Crea una nueva categoría en el catálogo.
     * 
     * @Valid: Activa las validaciones declaradas en CategoriaRequestDTO (@NotBlank, @Size).
     *         Si alguna falla, Spring rechaza la petición antes de entrar al método.
     * Retorna HTTP 201 Created junto con la entidad recién creada.
     */
    @PostMapping
    @Operation(summary = "Crear una categoría (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoría creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o nombre repetido")
    })
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO dto) {
        CategoriaResponseDTO creada = categoriaService.crear(dto);
        // 201 con header Location apuntando al recurso creado (Apunte 14)
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.getId()).toUri()).body(creada);
    }

    /**
     * DELETE /api/v1/catalogo/categorias/{id}
     * Elimina una categoría del catálogo, únicamente si no posee actividades asociadas.
     * Retorna HTTP 204 No Content en caso de éxito.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una categoría (ADMIN)",
            description = "Solo si no tiene actividades asociadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoría eliminada"),
            @ApiResponse(responseCode = "404", description = "La categoría no existe"),
            @ApiResponse(responseCode = "409", description = "Tiene actividades asociadas")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        categoriaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
