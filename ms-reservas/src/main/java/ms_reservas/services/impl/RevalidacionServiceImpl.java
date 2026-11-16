package ms_reservas.services.impl;

import ar.edu.utn.frc.bda.common.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_reservas.clients.CatalogoClient;
import ms_reservas.clients.OpenMeteoClient;
import ms_reservas.clients.OpenMeteoClient.PronosticoDia;
import ms_reservas.dtos.external.ActividadCatalogoDTO;
import ms_reservas.dtos.response.ResultadoRevalidacionDTO;
import ms_reservas.dtos.response.ResultadoRevalidacionDTO.CambioEstadoDTO;
import ms_reservas.entities.EstadoReserva;
import ms_reservas.entities.Reserva;
import ms_reservas.repositories.ReservaRepository;
import ms_reservas.services.EmailService;
import ms_reservas.services.EmailService.TipoNotificacion;
import ms_reservas.services.ReglasActividad;
import ms_reservas.services.RevalidacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Revalida las reservas PENDIENTE y CONFIRMADA con fecha futura, en este orden:
 * <ol>
 *   <li><b>Baja de la actividad</b>: si catálogo responde 404 / inactiva, se cancelan todas sus reservas.</li>
 *   <li><b>Cambio de temporada</b>: si la fecha quedó fuera de la temporada, se cancelan.</li>
 *   <li><b>Reducción de cupo</b>: si las reservas superan el nuevo cupo, se cancelan las PENDIENTE más
 *       recientes (las confirmadas se respetan).</li>
 *   <li><b>Clima</b>: si la actividad requiere buen clima y el pronóstico (hasta 16 días) es adverso,
 *       pasan a RECHAZADA_POR_CLIMA.</li>
 *   <li><b>Regla de 48 hs</b>: una reserva PENDIENTE a 48 hs de la excursión recibe un recordatorio; si a
 *       24 hs sigue PENDIENTE, se anula.</li>
 * </ol>
 * Cada cambio de estado se notifica por email. Si catálogo no responde, esa actividad no se toca
 * (no se cancela nada por una falla técnica) y se reintenta en la próxima ejecución.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevalidacionServiceImpl implements RevalidacionService {

    /** Open-Meteo pronostica hasta 16 días (hoy + 15). */
    private static final int DIAS_PRONOSTICO = 15;

    private final ReservaRepository reservaRepository;
    private final CatalogoClient catalogoClient;
    private final OpenMeteoClient openMeteoClient;
    private final EmailService emailService;

    @Override
    @Transactional
    public ResultadoRevalidacionDTO revalidar() {
        LocalDate hoy = LocalDate.now();
        List<Reserva> vigentes = reservaRepository
                .findByEstadoInAndFechaExcursionGreaterThanEqual(ReservaRepository.ESTADOS_ACTIVOS, hoy);

        List<CambioEstadoDTO> cambios = new ArrayList<>();
        int noVerificadas = 0;

        Map<Long, List<Reserva>> porActividad = vigentes.stream()
                .collect(Collectors.groupingBy(Reserva::getActividadId, TreeMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<Reserva>> entry : porActividad.entrySet()) {
            Optional<ActividadCatalogoDTO> actividad;
            try {
                actividad = Optional.of(catalogoClient.obtenerActividad(entry.getKey()));
            } catch (ResourceNotFoundException e) {
                actividad = Optional.empty();
            } catch (RestClientException e) {
                log.warn("Catálogo no disponible al revalidar la actividad {}: {}", entry.getKey(), e.getMessage());
                noVerificadas++;
                continue;
            }

            if (actividad.isEmpty()) {
                for (Reserva r : entry.getValue()) {
                    cambiar(r, EstadoReserva.CANCELADA,
                            "La actividad fue dada de baja del catálogo por el prestador", cambios);
                }
                continue;
            }
            revalidarActividad(actividad.get(), entry.getValue(), hoy, cambios);
        }

        int recordatorios = aplicarReglaDe48Horas(vigentes, hoy, cambios);
        reservaRepository.saveAll(vigentes);

        log.info("Revalidación: {} reservas revisadas, {} cambios de estado, {} recordatorios, {} actividades sin verificar",
                vigentes.size(), cambios.size(), recordatorios, noVerificadas);
        return new ResultadoRevalidacionDTO(LocalDateTime.now(), vigentes.size(), noVerificadas, recordatorios, cambios);
    }

    private void revalidarActividad(ActividadCatalogoDTO actividad, List<Reserva> reservas, LocalDate hoy,
                                    List<CambioEstadoDTO> cambios) {
        Map<LocalDate, List<Reserva>> porFecha = reservas.stream()
                .collect(Collectors.groupingBy(Reserva::getFechaExcursion, TreeMap::new, Collectors.toList()));

        for (Map.Entry<LocalDate, List<Reserva>> entry : porFecha.entrySet()) {
            LocalDate fecha = entry.getKey();
            List<Reserva> delDia = entry.getValue();

            // Temporada
            if (!ReglasActividad.enTemporada(actividad, fecha)) {
                for (Reserva r : delDia) {
                    cambiar(r, EstadoReserva.CANCELADA,
                            "El prestador modificó la temporada y la actividad ya no se realiza en esta fecha", cambios);
                }
                continue;
            }

            // Cupo: se cancelan las PENDIENTE más recientes hasta volver a entrar en el cupo
            int ocupados = delDia.stream().filter(this::activa).mapToInt(Reserva::getCantidadPersonas).sum();
            if (ocupados > actividad.cupoMaximo()) {
                List<Reserva> pendientesRecientesPrimero = delDia.stream()
                        .filter(r -> r.getEstado() == EstadoReserva.PENDIENTE)
                        .sorted(Comparator.comparing(Reserva::getFechaCreacion).reversed())
                        .toList();
                for (Reserva r : pendientesRecientesPrimero) {
                    if (ocupados <= actividad.cupoMaximo()) {
                        break;
                    }
                    cambiar(r, EstadoReserva.CANCELADA, "El prestador redujo el cupo a " + actividad.cupoMaximo()
                            + " personas y la reserva quedó fuera del cupo disponible", cambios);
                    ocupados -= r.getCantidadPersonas();
                }
                if (ocupados > actividad.cupoMaximo()) {
                    log.warn("Actividad {} el {}: las reservas CONFIRMADAS superan el nuevo cupo ({} > {})",
                            actividad.id(), fecha, ocupados, actividad.cupoMaximo());
                }
            }

            // Clima (solo dentro de la ventana de pronóstico)
            if (Boolean.TRUE.equals(actividad.requiereBuenClima()) && !fecha.isAfter(hoy.plusDays(DIAS_PRONOSTICO))) {
                Optional<PronosticoDia> pronostico = openMeteoClient.obtenerPronostico(
                        actividad.latitudInicio(), actividad.longitudInicio(), fecha);
                if (pronostico.isPresent()) {
                    String descripcion = ReglasActividad.describirClima(pronostico.get());
                    boolean adverso = ReglasActividad.climaAdverso(pronostico.get());
                    for (Reserva r : delDia) {
                        if (!activa(r)) {
                            continue;
                        }
                        r.setDescripcionClima(descripcion);
                        if (adverso) {
                            r.setAlertaClimatica(true);
                            cambiar(r, EstadoReserva.RECHAZADA_POR_CLIMA,
                                    "El pronóstico actualizado no permite realizar la excursión", cambios);
                        }
                    }
                }
            }
        }
    }

    /**
     * Regla de 48 hs (sección 14 del documento del equipo): a 48 hs de la excursión se pide confirmar
     * la reserva; si a 24 hs sigue PENDIENTE, se anula y se liberan los lugares.
     */
    private int aplicarReglaDe48Horas(List<Reserva> reservas, LocalDate hoy, List<CambioEstadoDTO> cambios) {
        int recordatorios = 0;
        for (Reserva r : reservas) {
            if (r.getEstado() != EstadoReserva.PENDIENTE) {
                continue;
            }
            if (!r.getFechaExcursion().isAfter(hoy.plusDays(1))) {
                cambiar(r, EstadoReserva.CANCELADA,
                        "La reserva no fue confirmada con al menos 24 hs de anticipación", cambios);
            } else if (!r.getFechaExcursion().isAfter(hoy.plusDays(2)) && !Boolean.TRUE.equals(r.getRecordatorioEnviado())) {
                r.setRecordatorioEnviado(true);
                emailService.notificar(r, TipoNotificacion.RECORDATORIO_CONFIRMACION);
                recordatorios++;
            }
        }
        return recordatorios;
    }

    private boolean activa(Reserva r) {
        return r.getEstado() == EstadoReserva.PENDIENTE || r.getEstado() == EstadoReserva.CONFIRMADA;
    }

    private void cambiar(Reserva r, EstadoReserva nuevo, String motivo, List<CambioEstadoDTO> cambios) {
        if (!activa(r)) {
            return; // ya fue cambiada por una regla anterior en esta misma ejecución
        }
        EstadoReserva anterior = r.getEstado();
        r.setEstado(nuevo);
        r.setMotivoEstado(motivo);
        cambios.add(new CambioEstadoDTO(r.getId(), r.getActividadId(), r.getFechaExcursion(), anterior, nuevo, motivo));
        emailService.notificar(r, nuevo == EstadoReserva.RECHAZADA_POR_CLIMA
                ? TipoNotificacion.RESERVA_RECHAZADA_POR_CLIMA
                : TipoNotificacion.RESERVA_CANCELADA);
    }
}
