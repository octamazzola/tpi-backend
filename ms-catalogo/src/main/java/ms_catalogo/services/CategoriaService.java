package ms_catalogo.services;

import java.util.List;
import ms_catalogo.dtos.CategoriaRequestDTO;
import ms_catalogo.dtos.CategoriaResponseDTO;

public interface CategoriaService {

    List<CategoriaResponseDTO> obtenerTodas();

    CategoriaResponseDTO obtenerPorId(Long id);

    CategoriaResponseDTO crear(CategoriaRequestDTO dto);

    void eliminar(Long id);
}

