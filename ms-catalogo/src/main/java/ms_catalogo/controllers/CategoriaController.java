package ms_catalogo.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ms_catalogo.dtos.CategoriaRequestDTO;
import ms_catalogo.dtos.CategoriaResponseDTO;
import ms_catalogo.services.CategoriaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
public class CategoriaController {

    private final CategoriaService categoriaService;

    /**
     * GET /api/v1/catalogo/categorias
     * Retorna el listado completo de categorías disponibles en el sistema.
     * Retorna HTTP 200 OK.
     */
    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> obtenerTodas() {
        return ResponseEntity.ok(categoriaService.obtenerTodas());
    }

    /**
     * GET /api/v1/catalogo/categorias/{id}
     * Busca una categoría específica por su clave primaria.
     * Si no existe, el Service lanzará ResourceNotFoundException (HTTP 404).
     */
    @GetMapping("/{id}")
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
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO dto) {
        CategoriaResponseDTO creada = categoriaService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /**
     * DELETE /api/v1/catalogo/categorias/{id}
     * Elimina una categoría del catálogo, únicamente si no posee actividades asociadas.
     * Retorna HTTP 204 No Content en caso de éxito.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        categoriaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
