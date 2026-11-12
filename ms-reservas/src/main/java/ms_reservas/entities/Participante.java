package ms_reservas.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Persona que asiste a la excursión dentro de una reserva (grupos o familias).
 * Tabla participantes con FK física reserva_id hacia reservas (relación 1..N).
 */
@Entity
@Table(name = "participantes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 15)
    private String dni;

    // Se usa para validar la edad mínima/máxima de la actividad el día de la excursión
    @Column(nullable = false)
    private LocalDate fechaNacimiento;

    @Column(length = 30)
    private String telefono;
}
