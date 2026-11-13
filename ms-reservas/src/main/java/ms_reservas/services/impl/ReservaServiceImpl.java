package ms_reservas.services.impl;

import ar.edu.utn.frc.bda.common.exceptions.BadRequestException;
import ar.edu.utn.frc.bda.common.exceptions.ConflictException;
import ar.edu.utn.frc.bda.common.exceptions.ForbiddenException;
import ar.edu.utn.frc.bda.common.exceptions.ResourceNotFoundException;
import ar.edu.utn.frc.bda.common.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.clients.CatalogoClient;
import ms_reservas.clients.GeoApiClient;
import ms_reservas.clients.OpenMeteoClient;
import ms_reservas.dtos.external.ActividadCatalogoDTO;
import ms_reservas.dtos.external.RutaGeoRequestDTO;
import ms_reservas.dtos.external.RutaGeoResponseDTO;
import ms_reservas.dtos.request.OrigenTuristaDTO;
import ms_reservas.dtos.request.ParticipanteDTO;
import ms_reservas.dtos.request.ReservaRequestDTO;
import ms_reservas.dtos.response.ReservaResponseDTO;
import ms_reservas.entities.EstadoReserva;
import ms_reservas.entities.Reserva;
import ms_reservas.mappers.ReservaMapper;
import ms_reservas.repositories.ReservaRepository;
import ms_reservas.services.EmailService;
import ms_reservas.services.EmailService.TipoNotificacion;
import ms_reservas.services.ReglasActividad;
import ms_reservas.services.ReservaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Orquestador del proceso de reserva (ver flujo de 8 pasos en MS_RESERVAS.md).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservaServiceImpl implements ReservaService {

    private static final String TURISTA_POR_DEFECTO = "turista1";

    private final ReservaRepository reservaRepository;
    private final CatalogoClient catalogoClient;
    private final GeoApiClient geoApiClient;
    private final OpenMeteoClient openMeteoClient;
    private final EmailService emailService;
    private final ReservaMapper reservaMapper;

    @Override
    @Transactional
    public ReservaResponseDTO crear(ReservaRequestDTO dto, UsuarioAutenticado usuario) {
        // 0. Identidad: si viene del gateway se usa el JWT; si no (prueba local), el body
        String turistaId = resolverTuristaId(dto, usuario);
        String emailTurista = resolverEmail(dto, usuario);
        validarDnisUnicos(dto.getParticipantes());
        int cantidadPersonas = dto.getParticipantes().size();

        // 1. Validar que la actividad exista y esté activa (404 si no)
        ActividadCatalogoDTO actividad = catalogoClient.obtenerActividad(dto.getActividadId());

        // 2. Restricciones de la actividad: temporada y edad de cada participante (400 si no cumple)
        if (!ReglasActividad.enTemporada(actividad, dto.getFechaExcursion())) {
            throw new BadRequestException("La actividad no se realiza en la fecha elegida: su temporada va del mes "
                    + actividad.mesInicioTemporada() + " al mes " + actividad.mesFinTemporada());
        }
        for (ParticipanteDTO p : dto.getParticipantes()) {
            ReglasActividad.validarEdad(actividad, p.getNombre(), p.getFechaNacimiento(), dto.getFechaExcursion())
                    .ifPresent(motivo -> {
                        throw new BadRequestException("Restricción de edad: " + motivo);
                    });
        }

        // 3. RN01 - Validar cupo: personas ya reservadas para esa fecha + las nuevas <= cupoMaximo (409 si no)
        int yaReservadas = reservaRepository.sumarPersonasReservadas(
                actividad.id(), dto.getFechaExcursion(), ReservaRepository.ESTADOS_ACTIVOS);
        if (yaReservadas + cantidadPersonas > actividad.cupoMaximo()) {
            throw new ConflictException("Cupo insuficiente para la fecha solicitada: quedan "
                    + Math.max(actividad.cupoMaximo() - yaReservadas, 0) + " lugares");
        }

        // 4. RN02 - Clima (solo si la actividad lo requiere); si Open-Meteo falla, se continúa sin alerta
        boolean alertaClimatica = false;
        String descripcionClima = null;
        if (Boolean.TRUE.equals(actividad.requiereBuenClima())) {
            var pronostico = openMeteoClient.obtenerPronostico(
                    actividad.latitudInicio(), actividad.longitudInicio(), dto.getFechaExcursion());
            if (pronostico.isPresent()) {
                alertaClimatica = ReglasActividad.climaAdverso(pronostico.get());
                descripcionClima = ReglasActividad.describirClima(pronostico.get());
            } else {
                descripcionClima = "Pronóstico no disponible para la fecha (servicio caído o fecha a más de 16 días). "
                        + "Se volverá a verificar automáticamente al acercarse la fecha.";
            }
        }

        // 5. RN03 - Traslado del turista al punto de encuentro (geoapi). Fallback si geoapi/OSRM no responde.
        OrigenTuristaDTO origen = dto.getOrigenTurista();
        Optional<RutaGeoResponseDTO> ruta = geoApiClient.calcularDistancia(new RutaGeoRequestDTO(
                new RutaGeoRequestDTO.Coordenada(origen.getLatitud(), origen.getLongitud()),
                new RutaGeoRequestDTO.Coordenada(actividad.latitudInicio(), actividad.longitudInicio())));

        // 6. Monto total (desnormalizado: no cambia si luego se modifica el precio en el catálogo)
        double montoTotal = actividad.precioBase() * cantidadPersonas;

        // 7. Persistir: PENDIENTE, salvo pronóstico adverso que la rechaza sin ocupar cupo
        Reserva reserva = Reserva.builder()
                .turistaId(turistaId)
                .emailTurista(emailTurista)
                .actividadId(actividad.id())
                .tituloActividad(actividad.titulo())
                .fechaExcursion(dto.getFechaExcursion())
                .cantidadPersonas(cantidadPersonas)
                .participantes(new java.util.ArrayList<>(reservaMapper.toParticipantes(dto.getParticipantes())))
                .montoTotal(montoTotal)
                .estado(alertaClimatica ? EstadoReserva.RECHAZADA_POR_CLIMA : EstadoReserva.PENDIENTE)
                .motivoEstado(alertaClimatica ? "Pronóstico adverso para la fecha de la excursión" : "Pendiente de confirmación por el prestador")
                .distanciaKm(ruta.map(RutaGeoResponseDTO::distanciaKm).orElse(null))
                .duracionEstimadaViaje(ruta.map(RutaGeoResponseDTO::duracionFormateada).orElse("No disponible"))
                .alertaClimatica(alertaClimatica)
                .descripcionClima(descripcionClima)
                .fechaCreacion(LocalDateTime.now())
                .build();
        Reserva guardada = reservaRepository.save(reserva);

        // 8. Notificar por email (asincrónico: un error de envío no afecta la reserva)
        emailService.notificar(guardada, TipoNotificacion.RESERVA_CREADA);
        return reservaMapper.toResponseDTO(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarTodas() {
        return reservaRepository.findAll().stream()
                .map(reservaMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarPorTurista(String turistaId) {
        return reservaRepository.findByTuristaIdOrderByFechaExcursionDesc(turistaId).stream()
                .map(reservaMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponseDTO obtenerPorId(Long id, UsuarioAutenticado usuario) {
        Reserva reserva = buscarPorId(id);
        validarAccesoTurista(reserva, usuario, "ver");
        return reservaMapper.toResponseDTO(reserva);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarPorActividad(Long actividadId) {
        return reservaRepository.findByActividadIdOrderByFechaExcursionAsc(actividadId).stream()
                .map(reservaMapper::toResponseDTO)
                .toList();
    }

    /**
     * F04 / RN05: PENDIENTE -> CONFIRMADA. Envía el voucher por email.
     */
    @Override
    @Transactional
    public ReservaResponseDTO confirmar(Long id) {
        Reserva reserva = buscarPorId(id);
        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new BadRequestException("Solo se puede confirmar una reserva en estado PENDIENTE (estado actual: "
                    + reserva.getEstado() + ")");
        }
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reserva.setMotivoEstado("Confirmada por el prestador");
        Reserva guardada = reservaRepository.save(reserva);
        emailService.notificar(guardada, TipoNotificacion.RESERVA_CONFIRMADA);
        return reservaMapper.toResponseDTO(guardada);
    }

    /**
     * F05 / RN04 / RN05: PENDIENTE o CONFIRMADA -> CANCELADA. Libera el cupo y notifica por email.
     */
    @Override
    @Transactional
    public ReservaResponseDTO cancelar(Long id, UsuarioAutenticado usuario) {
        Reserva reserva = buscarPorId(id);
        validarAccesoTurista(reserva, usuario, "cancelar");
        if (reserva.getEstado() == EstadoReserva.CANCELADA || reserva.getEstado() == EstadoReserva.RECHAZADA_POR_CLIMA) {
            throw new BadRequestException("No se puede cancelar una reserva que ya se encuentra en estado " + reserva.getEstado());
        }
        reserva.setEstado(EstadoReserva.CANCELADA);
        reserva.setMotivoEstado(usuario != null && usuario.esAdmin()
                ? "Anulada por el administrador"
                : "Cancelada por el turista");
        Reserva guardada = reservaRepository.save(reserva);
        emailService.notificar(guardada, TipoNotificacion.RESERVA_CANCELADA);
        return reservaMapper.toResponseDTO(guardada);
    }

    /**
     * Un usuario que no es ADMIN ni OPERADOR (es decir, un TURISTA) solo puede operar sobre sus
     * propias reservas. Sin usuario (petición directa sin gateway) no se restringe.
     */
    private void validarAccesoTurista(Reserva reserva, UsuarioAutenticado usuario, String accion) {
        if (usuario == null || usuario.esAdmin() || usuario.esOperador()) {
            return;
        }
        if (!reserva.getTuristaId().equals(usuario.username())) {
            throw new ForbiddenException("No tiene permiso para " + accion + " una reserva de otro turista");
        }
    }

    private void validarDnisUnicos(List<ParticipanteDTO> participantes) {
        Set<String> dnis = new HashSet<>();
        for (ParticipanteDTO p : participantes) {
            if (!dnis.add(p.getDni())) {
                throw new BadRequestException("El DNI " + p.getDni() + " está repetido entre los participantes");
            }
        }
    }

    private String resolverTuristaId(ReservaRequestDTO dto, UsuarioAutenticado usuario) {
        if (usuario != null) {
            return usuario.username();
        }
        return (dto.getTuristaId() != null && !dto.getTuristaId().isBlank())
                ? dto.getTuristaId()
                : TURISTA_POR_DEFECTO;
    }

    private String resolverEmail(ReservaRequestDTO dto, UsuarioAutenticado usuario) {
        if (usuario != null && usuario.email() != null) {
            return usuario.email();
        }
        if (dto.getEmailTurista() == null || dto.getEmailTurista().isBlank()) {
            throw new BadRequestException("No se pudo determinar el email del turista: el usuario no tiene email "
                    + "en Keycloak y no se envió 'emailTurista' en el cuerpo");
        }
        return dto.getEmailTurista();
    }

    private Reserva buscarPorId(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));
    }
}
