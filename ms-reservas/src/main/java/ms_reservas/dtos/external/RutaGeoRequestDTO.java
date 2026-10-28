package ms_reservas.dtos.external;

/**
 * Request de geoapi: dos puntos estructurados (origen y destino).
 */
public record RutaGeoRequestDTO(
    Coordenada origen,
    Coordenada destino
) {
    public record Coordenada(double latitud, double longitud) {}
}
