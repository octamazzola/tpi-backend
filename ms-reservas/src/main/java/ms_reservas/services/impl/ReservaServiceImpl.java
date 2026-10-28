package ms_reservas.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.clients.CatalogoClient;
import ms_reservas.clients.GeoApiClient;
import ms_reservas.clients.OpenMeteoClient;
import ms_reservas.dtos.external.ActividadCatalogoDTO;
import ms_reservas.dtos.external.RutaGeoRequestDTO;
import ms_reservas.dtos.external.RutaGeoResponseDTO;
import ms_reservas.dtos.request.OrigenTuristaDTO;
import ms_reservas.dtos.request.ReservaRequestDTO;
import ms_reservas.dtos.response.ReservaResponseDTO;
import ms_reservas.dtos.response.TrasladoEstimadoDTO;
import ms_reservas.entities.EstadoReserva;
import ms_reservas.entities.Reserva;
import ms_reservas.exceptions.BadRequestException;
import ms_reservas.exceptions.ResourceNotFoundException;
import ms_reservas.repositories.ReservaRepository;
import ms_reservas.services.EmailService;
import ms_reservas.services.ReservaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orquestador del proceso de reserva (ver flujo de 8 pasos en MS_RESERVAS.md).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservaServiceImpl implements ReservaService {

    private static final int PROBABILIDAD_LLUVIA_MAX = 70;
    private static final int WEATHER_CODE_LLUVIA = 61;

    private final ReservaRepository reservaRepository;
    private final CatalogoClient catalogoClient;
    private final GeoApiClient geoApiClient;
    private final OpenMeteoClient openMeteoClient;
    private final EmailService emailService;

    @Override
    @Transactional
    public ReservaResponseDTO crear(ReservaRequestDTO dto) {
        // 1. Validar que la actividad exista y esté activa (404 si no)
        ActividadCatalogoDTO actividad = catalogoClient.obtenerActividad(dto.getActividadId());

        // 2. Validar cupo: personas ya reservadas para esa fecha + las nuevas <= cupoMaximo
        int yaReservadas = reservaRepository.sumarPersonasReservadas(
                actividad.id(), dto.getFechaExcursion(),
                List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA));
        if (yaReservadas + dto.getCantidadPersonas() > actividad.cupoMaximo()) {
            throw new BadRequestException("Cupo insuficiente: quedan "
                    + Math.max(actividad.cupoMaximo() - yaReservadas, 0) + " lugares para esa fecha");
        }

        // 3. Clima (solo si la actividad lo requiere); si Open-Meteo falla, alertaClimatica = false
        boolean alertaClimatica = false;
        String descripcionClima = null;
        if (Boolean.TRUE.equals(actividad.requiereBuenClima())) {
            var pronostico = openMeteoClient.obtenerPronostico(
                    actividad.latitudInicio(), actividad.longitudInicio(), dto.getFechaExcursion());
            if (pronostico.isPresent()) {
                var p = pronostico.get();
                alertaClimatica = p.probabilidadLluvia() > PROBABILIDAD_LLUVIA_MAX
                        || p.weatherCode() >= WEATHER_CODE_LLUVIA;
                descripcionClima = "Probabilidad de lluvia: " + p.probabilidadLluvia() + "%, código de clima: "
                        + p.weatherCode() + ", temperatura máxima: " + p.temperaturaMax() + " °C";
            }
        }

        // 4. Calcular traslado del turista hasta el punto de encuentro (geoapi)
        OrigenTuristaDTO origen = dto.getOrigenTurista();
        RutaGeoResponseDTO ruta = geoApiClient.calcularDistancia(new RutaGeoRequestDTO(
                new RutaGeoRequestDTO.Coordenada(origen.getLatitud(), origen.getLongitud()),
                new RutaGeoRequestDTO.Coordenada(actividad.latitudInicio(), actividad.longitudInicio())));

        // 5. Calcular monto total
        double montoTotal = actividad.precioBase() * dto.getCantidadPersonas();

        // 6. Guardar la reserva
        Reserva reserva = Reserva.builder()
                .turistaId(dto.getTuristaId())
                .emailTurista(dto.getEmailTurista())
                .actividadId(actividad.id())
                .tituloActividad(actividad.titulo())
                .fechaExcursion(dto.getFechaExcursion())
                .cantidadPersonas(dto.getCantidadPersonas())
                .montoTotal(montoTotal)
                .estado(alertaClimatica ? EstadoReserva.RECHAZADA_POR_CLIMA : EstadoReserva.CONFIRMADA)
                .distanciaKm(ruta.distanciaKm())
                .duracionEstimadaViaje(ruta.duracionFormateada())
                .alertaClimatica(alertaClimatica)
                .descripcionClima(descripcionClima)
                .fechaCreacion(LocalDateTime.now())
                .build();
        Reserva guardada = reservaRepository.save(reserva);

        // 7. Notificar por email (un error de envío no corta la transacción)
        emailService.enviarConfirmacionReserva(guardada);

        // 8. Respuesta con el DTO completo (el controller responde 201 Created)
        return toResponseDTO(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarPorTurista(String turistaId) {
        return reservaRepository.findByTuristaId(turistaId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponseDTO obtenerPorId(Long id) {
        return toResponseDTO(buscarPorId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarPorActividad(Long actividadId) {
        return reservaRepository.findByActividadId(actividadId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public ReservaResponseDTO confirmar(Long id) {
        Reserva reserva = buscarPorId(id);
        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new BadRequestException("Solo se puede confirmar una reserva en estado PENDIENTE");
        }
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        return toResponseDTO(reservaRepository.save(reserva));
    }

    @Override
    @Transactional
    public ReservaResponseDTO cancelar(Long id) {
        Reserva reserva = buscarPorId(id);
        reserva.setEstado(EstadoReserva.CANCELADA);
        return toResponseDTO(reservaRepository.save(reserva));
    }

    private Reserva buscarPorId(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));
    }

    private ReservaResponseDTO toResponseDTO(Reserva r) {
        return ReservaResponseDTO.builder()
                .id(r.getId())
                .turistaId(r.getTuristaId())
                .emailTurista(r.getEmailTurista())
                .actividadId(r.getActividadId())
                .tituloActividad(r.getTituloActividad())
                .fechaExcursion(r.getFechaExcursion())
                .cantidadPersonas(r.getCantidadPersonas())
                .montoTotal(r.getMontoTotal())
                .estado(r.getEstado())
                .trasladoEstimado(TrasladoEstimadoDTO.builder()
                        .distanciaKm(r.getDistanciaKm())
                        .duracionFormateada(r.getDuracionEstimadaViaje())
                        .build())
                .alertaClimatica(r.getAlertaClimatica())
                .descripcionClima(r.getDescripcionClima())
                .fechaCreacion(r.getFechaCreacion())
                .build();
    }
}
