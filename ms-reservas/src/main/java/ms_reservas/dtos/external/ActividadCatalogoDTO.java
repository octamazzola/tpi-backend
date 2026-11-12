package ms_reservas.dtos.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Vista de una actividad de ms-catalogo con solo los datos que reservas necesita.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActividadCatalogoDTO(
    Long id,
    String titulo,
    Double precioBase,
    Integer cupoMaximo,
    Double latitudInicio,
    Double longitudInicio,
    String direccionPuntoEncuentro,
    Boolean requiereBuenClima,
    Integer edadMinima,
    Integer edadMaxima,
    Integer mesInicioTemporada,
    Integer mesFinTemporada,
    Boolean activa
) {}
