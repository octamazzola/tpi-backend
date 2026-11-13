package ms_reservas.controllers;

import ar.edu.utn.frc.bda.common.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ms_reservas.dtos.request.ReservaRequestDTO;
import ms_reservas.dtos.response.ReservaResponseDTO;
import ms_reservas.dtos.response.ResultadoRevalidacionDTO;
import ms_reservas.services.ReservaService;
import ms_reservas.services.RevalidacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST de reservas.
 *
 * Los roles los valida el api-gateway. La identidad del usuario llega en los headers X-User-*
 * que el gateway arma a partir del JWT (ver UserHeaders en common-lib). Si la petición no pasa
 * por el gateway (prueba local directa al 8082), se usan los valores del body / query param.
 */
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;
    private final RevalidacionService revalidacionService;

    /**
     * POST /api/v1/reservas
     * Crea una reserva ejecutando toda la orquestación (TURISTA).
     */
    @PostMapping
    public ResponseEntity<ReservaResponseDTO> crear(@Valid @RequestBody ReservaRequestDTO dto, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservaService.crear(dto, usuario(request)));
    }

    /**
     * GET /api/v1/reservas
     * Lista la totalidad de las reservas del sistema (ADMIN).
     */
    @GetMapping
    public ResponseEntity<List<ReservaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(reservaService.listarTodas());
    }

    /**
     * GET /api/v1/reservas/mis-reservas
     * Lista las reservas del turista autenticado (TURISTA). El turista sale del token JWT;
     * el parámetro turistaId solo se usa en pruebas locales sin gateway.
     */
    @GetMapping("/mis-reservas")
    public ResponseEntity<List<ReservaResponseDTO>> misReservas(
            @RequestParam(required = false, defaultValue = "turista1") String turistaId,
            HttpServletRequest request) {
        String turista = UsuarioAutenticado.desde(request).map(UsuarioAutenticado::username).orElse(turistaId);
        return ResponseEntity.ok(reservaService.listarPorTurista(turista));
    }

    /**
     * GET /api/v1/reservas/{id}
     * Detalle completo de una reserva. Un TURISTA solo puede ver las propias.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDTO> obtenerPorId(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(reservaService.obtenerPorId(id, usuario(request)));
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
     * Cancela la reserva (TURISTA solo las propias, ADMIN cualquiera).
     */
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(reservaService.cancelar(id, usuario(request)));
    }

    /**
     * POST /api/v1/reservas/revalidaciones
     * Ejecuta la revalidación de reservas vigentes (ADMIN) ante cambios en la actividad o el pronóstico.
     */
    @PostMapping("/revalidaciones")
    public ResponseEntity<ResultadoRevalidacionDTO> revalidar() {
        return ResponseEntity.ok(revalidacionService.revalidar());
    }

    /** Usuario propagado por el gateway, o null si la petición llegó directo (sin gateway). */
    private UsuarioAutenticado usuario(HttpServletRequest request) {
        return UsuarioAutenticado.desde(request).orElse(null);
    }
}
