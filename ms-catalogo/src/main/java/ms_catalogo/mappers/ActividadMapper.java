package ms_catalogo.mappers;

import lombok.RequiredArgsConstructor;
import ms_catalogo.dtos.ActividadRequestDTO;
import ms_catalogo.dtos.ActividadResponseDTO;
import ms_catalogo.entities.Actividad;
import ms_catalogo.entities.Categoria;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformar entre la entidad Actividad y sus DTOs correspondientes.
 */
@Component
@RequiredArgsConstructor
public class ActividadMapper {

    private final CategoriaMapper categoriaMapper;

    public Actividad toEntity(ActividadRequestDTO dto, Categoria categoria) {
        if (dto == null) return null;
        return Actividad.builder()
                .titulo(dto.getTitulo())
                .descripcion(dto.getDescripcion())
                .categoria(categoria)
                .dificultad(dto.getDificultad())
                .precioBase(dto.getPrecioBase())
                .cupoMaximo(dto.getCupoMaximo())
                .latitudInicio(dto.getLatitudInicio())
                .longitudInicio(dto.getLongitudInicio())
                .direccionPuntoEncuentro(dto.getDireccionPuntoEncuentro())
                .requiereBuenClima(dto.getRequiereBuenClima())
                .activa(true)
                .build();
    }

    public ActividadResponseDTO toResponseDTO(Actividad entity) {
        if (entity == null) return null;
        return ActividadResponseDTO.builder()
                .id(entity.getId())
                .titulo(entity.getTitulo())
                .descripcion(entity.getDescripcion())
                .categoria(categoriaMapper.toResponseDTO(entity.getCategoria()))
                .dificultad(entity.getDificultad())
                .precioBase(entity.getPrecioBase())
                .cupoMaximo(entity.getCupoMaximo())
                .latitudInicio(entity.getLatitudInicio())
                .longitudInicio(entity.getLongitudInicio())
                .direccionPuntoEncuentro(entity.getDireccionPuntoEncuentro())
                .requiereBuenClima(entity.getRequiereBuenClima())
                .activa(entity.getActiva())
                .build();
    }

    public void updateEntityFromDTO(Actividad entity, ActividadRequestDTO dto, Categoria categoria) {
        entity.setTitulo(dto.getTitulo());
        entity.setDescripcion(dto.getDescripcion());
        entity.setCategoria(categoria);
        entity.setDificultad(dto.getDificultad());
        entity.setPrecioBase(dto.getPrecioBase());
        entity.setCupoMaximo(dto.getCupoMaximo());
        entity.setLatitudInicio(dto.getLatitudInicio());
        entity.setLongitudInicio(dto.getLongitudInicio());
        entity.setDireccionPuntoEncuentro(dto.getDireccionPuntoEncuentro());
        entity.setRequiereBuenClima(dto.getRequiereBuenClima());
    }
}
