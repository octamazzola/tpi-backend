package ms_catalogo.dtos;

import lombok.*;
import ms_catalogo.entities.Dificultad;

/**
 * DTO para enviar los datos de una actividad al cliente en las consultas GET, POST y PUT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActividadResponseDTO {

    private Long id;
    private String titulo;
    private String descripcion;
    private CategoriaResponseDTO categoria;
    private Dificultad dificultad;
    private Double precioBase;
    private Integer cupoMaximo;
    private Double latitudInicio;
    private Double longitudInicio;
    private String direccionPuntoEncuentro;
    private String ciudad;
    private Boolean requiereBuenClima;
    private Integer edadMinima;
    private Integer edadMaxima;
    private Integer mesInicioTemporada;
    private Integer mesFinTemporada;
    private Boolean activa;
}
