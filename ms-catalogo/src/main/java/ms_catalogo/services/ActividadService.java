package ms_catalogo.services;

import java.util.List;
import ms_catalogo.dtos.ActividadRequestDTO;
import ms_catalogo.dtos.ActividadResponseDTO;
import ms_catalogo.dtos.DisponibilidadDTO;
import ms_catalogo.entities.Dificultad;

public interface ActividadService {

    List<ActividadResponseDTO> listarActividades(Long categoriaId, Dificultad dificultad, Double precioMax);

    ActividadResponseDTO obtenerPorId(Long id);

    DisponibilidadDTO consultarDisponibilidad(Long id);

    ActividadResponseDTO crear(ActividadRequestDTO dto);

    ActividadResponseDTO actualizar(Long id, ActividadRequestDTO dto);

    void darDeBaja(Long id);
}
