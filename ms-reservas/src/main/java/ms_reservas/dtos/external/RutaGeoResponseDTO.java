package ms_reservas.dtos.external;

public record RutaGeoResponseDTO(
    double distanciaKm,
    int duracionMinutos,
    String duracionFormateada // Ej: "2 hs 15 min"
) {}
