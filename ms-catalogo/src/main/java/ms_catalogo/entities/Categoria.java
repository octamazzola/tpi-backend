package ms_catalogo.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity

// lo que hace esto es que le avisa a hibernate que esta clase
// representa una tabla en la base de datos

@Table(name = "categorias")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Nos ahorra escribir a mano
// los getters, setters, constructores y el patrón builder.

public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // aca se define que id es la pk de la tabla, y que se autoincremente solo.

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

}
