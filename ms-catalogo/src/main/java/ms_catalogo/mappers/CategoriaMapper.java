package ms_catalogo.mappers;

import ms_catalogo.dtos.CategoriaRequestDTO;
import ms_catalogo.dtos.CategoriaResponseDTO;
import ms_catalogo.entities.Categoria;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformar entre la entidad Categoria y sus DTOs correspondientes.
 */
@Component
public class CategoriaMapper {

    public Categoria toEntity(CategoriaRequestDTO dto) {
        if (dto == null) return null;
        return Categoria.builder()
                .nombre(dto.getNombre())
                .descripcion(dto.getDescripcion())
                .build();
    }

    public CategoriaResponseDTO toResponseDTO(Categoria entity) {
        if (entity == null) return null;
        return CategoriaResponseDTO.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .descripcion(entity.getDescripcion())
                .build();
    }
}
