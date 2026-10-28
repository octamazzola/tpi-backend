package ms_reservas.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ms_reservas.dtos.request.ReservaRequestDTO;
import ms_reservas.dtos.response.ReservaResponseDTO;
import ms_reservas.services.ReservaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST de reservas. Los roles son validados por el api-gateway.
 */
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    /**
     * POST /api/v1/reservas
     * Crea una reserva ejecutando toda la orquestación (TURISTA).
     */
    @PostMapping
    public ResponseEntity<ReservaResponseDTO> crear(@Valid @RequestBody ReservaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(dto));
    }

    /**
     * GET /api/v1/reservas/mis-reservas?turistaId=...
     * Lista las reservas del turista (TURISTA).
     */
    @GetMapping("/mis-reservas")
    public ResponseEntity<List<ReservaResponseDTO>> misReservas(@RequestParam String turistaId) {
        return ResponseEntity.ok(reservaService.listarPorTurista(turistaId));
    }

    /**
     * GET /api/v1/reservas/{id}
     * Detalle completo de una reserva (TURISTA, OPERADOR).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.obtenerPorId(id));
    }

    /**
     * GET /api/v1/reservas/actividad/{actividadId}
     * Lista todas las reservas de una excursión (OPERADOR, ADMIN).
     */
    @GetMapping("/actividad/{actividadId}")
    public ResponseEntity<List<ReservaResponseDTO>> listarPorActividad(@PathVariable Long actividadId) {
        return ResponseEntity.ok(reservaService.listarPorActividad(actividadId));
    }

    /**
     * PUT /api/v1/reservas/{id}/confirmar
     * Confirma una reserva pendiente (OPERADOR, ADMIN).
     */
    @PutMapping("/{id}/confirmar")
    public ResponseEntity<ReservaResponseDTO> confirmar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.confirmar(id));
    }

    /**
     * PUT /api/v1/reservas/{id}/cancelar
     * Cancela la reserva (TURISTA, ADMIN).
     */
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.cancelar(id));
    }
}
