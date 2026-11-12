package ms_reservas.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    // Identificador del turista (obtenido del token JWT o del request)
    @Column(nullable = false, length = 100)
    private String turistaId;

    @Column(nullable = false, length = 150)
    private String emailTurista;

    // Referencia lógica al microservicio de Catálogo
    @Column(nullable = false)
    private Long actividadId;

    @Column(nullable = false)
    private String tituloActividad;

    @Column(nullable = false)
    private LocalDate fechaExcursion;

    @Column(nullable = false)
    private Integer cantidadPersonas;

    @Column(nullable = false)
    private Double montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoReserva estado;

    // Datos calculados mediante geoapi / OSRM
    private Double distanciaKm;
    private String duracionEstimadaViaje;

    // Datos meteorológicos de Open-Meteo
    private Boolean alertaClimatica;
    private String descripcionClima;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;
}
