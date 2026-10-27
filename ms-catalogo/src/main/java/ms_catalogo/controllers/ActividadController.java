package ms_catalogo.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * como por otros microservicios (por ejemplo, ms-reservas para disponibilidad).
 */
@RestController
@RequestMapping("/api/v1/catalogo/actividades")
@RequiredArgsConstructor
public class ActividadController {

    private final ActividadService actividadService;

    /**
     * GET /api/v1/catalogo/actividades
     * Lista actividades activas con soporte para filtros combinados opcionales.
     * Ejemplo: /api/v1/catalogo/actividades?categoriaId=1&dificultad=MEDIA&precioMax=50000
     */
    @GetMapping
    public ResponseEntity<List<ActividadResponseDTO>> listar(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Dificultad dificultad,
            @RequestParam(required = false) Double precioMax) {
        return ResponseEntity.ok(actividadService.listarActividades(categoriaId, dificultad, precioMax));
    }

    /**
     * GET /api/v1/catalogo/actividades/{id}
     * Detalle completo de una excursión por su identificador.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ActividadResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(actividadService.obtenerPorId(id));
    }

    /**
     * GET /api/v1/catalogo/actividades/{id}/disponibilidad
     * Endpoint inter-servicio: consultado por ms-reservas para validar cupos máximos
     * y si la actividad sigue activa antes de generar una reserva.
     */
    @GetMapping("/{id}/disponibilidad")
    public ResponseEntity<DisponibilidadDTO> consultarDisponibilidad(@PathVariable Long id) {
        return ResponseEntity.ok(actividadService.consultarDisponibilidad(id));
    }

    /**
     * POST /api/v1/catalogo/actividades
     * Registra una nueva actividad en el catálogo con sus datos geográficos y cupos.
     * Retorna HTTP 201 Created.
     */
    @PostMapping
    public ResponseEntity<ActividadResponseDTO> crear(@Valid @RequestBody ActividadRequestDTO dto) {
        ActividadResponseDTO creada = actividadService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /**
     * PUT /api/v1/catalogo/actividades/{id}
     * Modifica los datos de una actividad existente.
     * Retorna HTTP 200 OK.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ActividadResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActividadRequestDTO dto) {
        return ResponseEntity.ok(actividadService.actualizar(id, dto));
    }

    /**
     * PATCH /api/v1/catalogo/actividades/{id}
     * Actualiza parcialmente uno o más campos de una actividad existente (por ejemplo, tarifa o cupo).
     * Solo los campos no nulos recibidos en el payload serán modificados.
     * Retorna HTTP 200 OK.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ActividadResponseDTO> actualizarParcial(
            @PathVariable Long id,
            @Valid @RequestBody ms_catalogo.dtos.ActividadPatchDTO dto) {
        return ResponseEntity.ok(actividadService.actualizarParcial(id, dto));
    }

    /**
     * DELETE /api/v1/catalogo/actividades/{id}
     * Aplica la BAJA LÓGICA (activa = false). No destruye datos en la base de datos SQL
     * para preservar la trazabilidad de reservas pasadas.
     * Retorna HTTP 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Long id) {
        actividadService.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }
}
