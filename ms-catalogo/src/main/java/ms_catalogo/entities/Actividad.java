package ms_catalogo.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entidad que representa una Actividad turística o excursión en el catálogo.
 */
@Entity
@Table(name = "actividades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Actividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 1000)
    private String descripcion;

    // Relación Muchos a Uno: Muchas actividades pertenecen a una categoría.
    // En la base de datos se crea la clave foránea 'categoria_id'.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    // EnumType.STRING almacena el texto ("BAJA", "MEDIA", "ALTA") en lugar del índice ordinal (0, 1, 2).
    // Evita inconsistencias ante cambios en el orden del enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Dificultad dificultad;

    @Column(nullable = false)
    private Double precioBase;

    @Column(nullable = false)
    private Integer cupoMaximo;

    // Coordenadas geográficas del punto de inicio/encuentro (utilizadas por geoapi / OSRM)
    @Column(nullable = false)
    private Double latitudInicio;

    @Column(nullable = false)
    private Double longitudInicio;

    @Column(length = 255)
    private String direccionPuntoEncuentro;

    // Bandera consultada por ms-reservas para determinar si se debe validar el pronóstico del clima con Open-Meteo
    @Column(nullable = false)
    private Boolean requiereBuenClima;

    // Baja lógica: DELETE /actividades/{id} cambia activa a false para no romper reservas históricas
    @Builder.Default
    @Column(nullable = false)
    private Boolean activa = true;
}
