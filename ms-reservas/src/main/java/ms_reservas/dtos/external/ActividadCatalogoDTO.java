package ms_reservas.dtos.external;

public record ActividadCatalogoDTO(
    Long id,
    String titulo,
    Double precioBase,
    Integer cupoMaximo,
    Double latitudInicio,
    Double longitudInicio,
    String direccionPuntoEncuentro,
    Boolean requiereBuenClima,
    Boolean activa
) {}
