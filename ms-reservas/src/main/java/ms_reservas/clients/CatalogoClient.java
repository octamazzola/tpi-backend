package ms_reservas.clients;

import lombok.RequiredArgsConstructor;
import ms_reservas.dtos.external.ActividadCatalogoDTO;
import ms_reservas.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia ms-catalogo (8081).
 */
@Component
@RequiredArgsConstructor
public class CatalogoClient {

    private static final String MSG_NO_DISPONIBLE = "La actividad seleccionada no existe o no se encuentra disponible";

    private final RestClient restClient;

    @Value("${servicios.catalogo.url}")
    private String catalogoUrl;

    /**
     * Obtiene una actividad del catálogo. Lanza 404 si no existe o si está inactiva.
     */
    public ActividadCatalogoDTO obtenerActividad(Long id) {
        ActividadCatalogoDTO actividad;
        try {
            actividad = restClient.get()
                    .uri(catalogoUrl + "/actividades/{id}", id)
                    .retrieve()
                    .body(ActividadCatalogoDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException(MSG_NO_DISPONIBLE);
        }

        if (actividad == null || Boolean.FALSE.equals(actividad.activa())) {
            throw new ResourceNotFoundException(MSG_NO_DISPONIBLE);
        }
        return actividad;
    }
}
