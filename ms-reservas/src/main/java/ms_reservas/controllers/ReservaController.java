package ms_reservas.controllers;

import ar.edu.utn.frc.bda.common.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Reservas", description = "Proceso central: crear, consultar, confirmar, cancelar y revalidar reservas")
public class ReservaController {

    private final ReservaService reservaService;
    private final RevalidacionService revalidacionService;

    @PostMapping
    @Operation(summary = "Crear una reserva (TURISTA, ADMIN)",
            description = "Orquesta catálogo (actividad y restricciones), Open-Meteo (si requiere buen clima) y geoapi "
                    + "(traslado). Queda PENDIENTE o RECHAZADA_POR_CLIMA y se notifica por email. "
                    + "El turista y su email salen del token JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva registrada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (formato, campos faltantes, fecha pasada)"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o fue dada de baja"),
            @ApiResponse(responseCode = "409", description = "Cupo insuficiente para la fecha (RN01)"),
            @ApiResponse(responseCode = "422", description = "No cumple las reglas de la actividad: edad, temporada o DNI repetido"),
            @ApiResponse(responseCode = "502", description = "ms-catalogo no responde")
    })
    public ResponseEntity<ReservaResponseDTO> crear(@Valid @RequestBody ReservaRequestDTO dto, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservaService.crear(dto, usuario(request)));
    }

    @GetMapping
    @Operation(summary = "Listar todas las reservas (ADMIN)", description = "Auditoría global del sistema.")
    @ApiResponse(responseCode = "200", description = "Listado de reservas")
    public ResponseEntity<List<ReservaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(reservaService.listarTodas());
    }

    @GetMapping("/mis-reservas")
    @Operation(summary = "Listar mis reservas (TURISTA, ADMIN)",
            description = "Reservas del usuario del token, de la más próxima a la más lejana.")
    @ApiResponse(responseCode = "200", description = "Reservas del turista autenticado")
    public ResponseEntity<List<ReservaResponseDTO>> misReservas(
            @Parameter(description = "Solo para pruebas locales sin gateway: vía gateway se ignora y se usa el token")
            @RequestParam(required = false, defaultValue = "turista1") String turistaId,
            HttpServletRequest request) {
        String turista = UsuarioAutenticado.desde(request).map(UsuarioAutenticado::username).orElse(turistaId);
        return ResponseEntity.ok(reservaService.listarPorTurista(turista));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una reserva (autenticado)",
            description = "Detalle con participantes, monto, traslado y clima. Un TURISTA solo puede ver las propias.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva encontrada"),
            @ApiResponse(responseCode = "403", description = "La reserva pertenece a otro turista"),
            @ApiResponse(responseCode = "404", description = "La reserva no existe")
    })
    public ResponseEntity<ReservaResponseDTO> obtenerPorId(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(reservaService.obtenerPorId(id, usuario(request)));
    }

    @GetMapping("/actividad/{actividadId}")
    @Operation(summary = "Listar reservas de una actividad (OPERADOR, ADMIN)",
            description = "Contingente de una excursión, ordenado por fecha.")
    @ApiResponse(responseCode = "200", description = "Reservas de la actividad")
    public ResponseEntity<List<ReservaResponseDTO>> listarPorActividad(@PathVariable Long actividadId) {
        return ResponseEntity.ok(reservaService.listarPorActividad(actividadId));
    }

    @PutMapping("/{id}/confirmar")
    @Operation(summary = "Confirmar una reserva (OPERADOR, ADMIN)",
            description = "PENDIENTE → CONFIRMADA. Envía el voucher por email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva confirmada"),
            @ApiResponse(responseCode = "400", description = "La reserva no está PENDIENTE (RN05)"),
            @ApiResponse(responseCode = "404", description = "La reserva no existe")
    })
    public ResponseEntity<ReservaResponseDTO> confirmar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.confirmar(id));
    }

    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar una reserva (TURISTA titular, ADMIN)",
            description = "PENDIENTE o CONFIRMADA → CANCELADA. Libera el cupo y notifica por email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva cancelada"),
            @ApiResponse(responseCode = "400", description = "Ya está en un estado final (RN04, RN05)"),
            @ApiResponse(responseCode = "403", description = "La reserva pertenece a otro turista"),
            @ApiResponse(responseCode = "404", description = "La reserva no existe")
    })
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(reservaService.cancelar(id, usuario(request)));
    }

    @PostMapping("/revalidaciones")
    @Operation(summary = "Revalidar reservas vigentes (ADMIN)",
            description = "Ejecuta en el momento la revalidación que corre sola cada hora: baja de actividad, "
                    + "cambio de temporada, reducción de cupo, pronóstico adverso y regla de 48 hs.")
    @ApiResponse(responseCode = "200", description = "Resumen de la ejecución con los cambios de estado")
    public ResponseEntity<ResultadoRevalidacionDTO> revalidar() {
        return ResponseEntity.ok(revalidacionService.revalidar());
    }

    /** Usuario propagado por el gateway, o null si la petición llegó directo (sin gateway). */
    private UsuarioAutenticado usuario(HttpServletRequest request) {
        return UsuarioAutenticado.desde(request).orElse(null);
    }
}
