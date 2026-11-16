package ms_reservas.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identificador del turista: preferred_username del token JWT (lo propaga el api-gateway)
    @Column(nullable = false, length = 100)
    private String turistaId;

    @Column(nullable = false, length = 150)
    private String emailTurista;

    // Referencia lógica al microservicio de Catálogo (no es FK física: Database-per-Service)
    @Column(nullable = false)
    private Long actividadId;

    // Desnormalizado: si el prestador cambia el título luego, la reserva conserva el original
    @Column(nullable = false)
    private String tituloActividad;

    @Column(nullable = false)
    private LocalDate fechaExcursion;

    // Siempre igual a participantes.size(); se guarda para sumar cupos con una sola consulta
    @Column(nullable = false)
    private Integer cantidadPersonas;

    @Column(nullable = false)
    private Double montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoReserva estado;

    // Por qué la reserva quedó en su estado actual (ej. "Pronóstico de lluvia del 85%")
    @Column(length = 255)
    private String motivoEstado;

    // Datos calculados mediante geoapi / OSRM
    private Double distanciaKm;
    private String duracionEstimadaViaje;

    // Datos meteorológicos de Open-Meteo
    private Boolean alertaClimatica;
    private String descripcionClima;

    // Regla de 48 hs: indica si ya se envió el recordatorio para confirmar la reserva
    @Builder.Default
    @Column(nullable = false)
    private Boolean recordatorioEnviado = false;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    // Relación 1..N con FK física reserva_id en la tabla participantes
    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "reserva_id", nullable = false)
    private List<Participante> participantes = new ArrayList<>();
}
