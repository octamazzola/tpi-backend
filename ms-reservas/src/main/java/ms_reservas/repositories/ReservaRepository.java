package ms_reservas.repositories;

import ms_reservas.entities.EstadoReserva;
import ms_reservas.entities.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Repositorio de reservas con consultas por turista, por actividad y de ocupación de cupo.
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** Estados que ocupan cupo y que la revalidación debe vigilar. */
    List<EstadoReserva> ESTADOS_ACTIVOS = List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);

    List<Reserva> findByTuristaIdOrderByFechaExcursionDesc(String turistaId);

    List<Reserva> findByActividadIdOrderByFechaExcursionAsc(Long actividadId);

    /** Reservas que siguen vigentes (pendientes o confirmadas) desde una fecha en adelante. */
    List<Reserva> findByEstadoInAndFechaExcursionGreaterThanEqual(Collection<EstadoReserva> estados, LocalDate desde);

    /**
     * Suma las personas ya reservadas para una actividad en una fecha, considerando
     * únicamente los estados indicados (los que efectivamente ocupan cupo).
     */
    @Query("SELECT COALESCE(SUM(r.cantidadPersonas), 0) FROM Reserva r " +
           "WHERE r.actividadId = :actividadId AND r.fechaExcursion = :fecha AND r.estado IN :estados")
    Integer sumarPersonasReservadas(@Param("actividadId") Long actividadId,
                                    @Param("fecha") LocalDate fecha,
                                    @Param("estados") List<EstadoReserva> estados);
}
