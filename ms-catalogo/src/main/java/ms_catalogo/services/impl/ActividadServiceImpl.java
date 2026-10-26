package ms_catalogo.services.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import ms_catalogo.dtos.ActividadPatchDTO;
import ms_catalogo.dtos.ActividadRequestDTO;
import ms_catalogo.dtos.ActividadResponseDTO;
import ms_catalogo.dtos.DisponibilidadDTO;
import ms_catalogo.entities.Actividad;
import ms_catalogo.entities.Categoria;
import ms_catalogo.entities.Dificultad;
import ms_catalogo.exceptions.ResourceNotFoundException;
import ms_catalogo.mappers.ActividadMapper;
import ms_catalogo.repositories.ActividadRepository;
import ms_catalogo.repositories.CategoriaRepository;
import ms_catalogo.services.ActividadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActividadServiceImpl implements ActividadService {

    private final ActividadRepository actividadRepository;
    private final CategoriaRepository categoriaRepository;
    private final ActividadMapper actividadMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ActividadResponseDTO> listarActividades(Long categoriaId, Dificultad dificultad, Double precioMax) {
        List<Actividad> actividades = actividadRepository.findByActivaTrue();

        // Aplicamos los filtros opcionales solicitados en el README
        return actividades.stream()
                .filter(a -> categoriaId == null || a.getCategoria().getId().equals(categoriaId))
                .filter(a -> dificultad == null || a.getDificultad().equals(dificultad))
                .filter(a -> precioMax == null || a.getPrecioBase() <= precioMax)
                .map(actividadMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ActividadResponseDTO obtenerPorId(Long id) {
        Actividad actividad = actividadRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada con ID: " + id));
        return actividadMapper.toResponseDTO(actividad);
    }

    @Override
    @Transactional(readOnly = true)
    public DisponibilidadDTO consultarDisponibilidad(Long id) {
        Actividad actividad = actividadRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada con ID: " + id));

        return DisponibilidadDTO.builder()
                .actividadId(actividad.getId())
                .titulo(actividad.getTitulo())
                .cupoMaximo(actividad.getCupoMaximo())
                .disponible(actividad.getActiva() && actividad.getCupoMaximo() > 0)
                .build();
    }

    @Override
    @Transactional
    public ActividadResponseDTO crear(ActividadRequestDTO dto) {
        Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + dto.getCategoriaId()));

        Actividad nuevaActividad = actividadMapper.toEntity(dto, categoria);
        Actividad guardada = actividadRepository.save(nuevaActividad);

        return actividadMapper.toResponseDTO(guardada);
    }

    @Override
    @Transactional
    public ActividadResponseDTO actualizar(Long id, ActividadRequestDTO dto) {
        Actividad actividadExistente = actividadRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada con ID: " + id));

        Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + dto.getCategoriaId()));

        actividadMapper.updateEntityFromDTO(actividadExistente, dto, categoria);
        Actividad actualizada = actividadRepository.save(actividadExistente);

        return actividadMapper.toResponseDTO(actualizada);
    }

    @Override
    @Transactional
    public ActividadResponseDTO actualizarParcial(Long id, ActividadPatchDTO dto) {
        Actividad actividad = actividadRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada con ID: " + id));

        if (dto.getTitulo() != null && !dto.getTitulo().isBlank()) {
            actividad.setTitulo(dto.getTitulo().trim());
        }
        if (dto.getDescripcion() != null) {
            actividad.setDescripcion(dto.getDescripcion().trim());
        }
        if (dto.getPrecioBase() != null) {
            actividad.setPrecioBase(dto.getPrecioBase());
        }
        if (dto.getCupoMaximo() != null) {
            actividad.setCupoMaximo(dto.getCupoMaximo());
        }
        if (dto.getDificultad() != null) {
            actividad.setDificultad(dto.getDificultad());
        }
        if (dto.getLatitudInicio() != null) {
            actividad.setLatitudInicio(dto.getLatitudInicio());
        }
        if (dto.getLongitudInicio() != null) {
            actividad.setLongitudInicio(dto.getLongitudInicio());
        }
        if (dto.getDireccionPuntoEncuentro() != null) {
            actividad.setDireccionPuntoEncuentro(dto.getDireccionPuntoEncuentro().trim());
        }
        if (dto.getRequiereBuenClima() != null) {
            actividad.setRequiereBuenClima(dto.getRequiereBuenClima());
        }
        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + dto.getCategoriaId()));
            actividad.setCategoria(categoria);
        }

        Actividad actualizada = actividadRepository.save(actividad);
        return actividadMapper.toResponseDTO(actualizada);
    }

    @Override
    @Transactional
    public void darDeBaja(Long id) {
        Actividad actividad = actividadRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada con ID: " + id));

        // Baja lógica: se cambia activa a false para no romper integridad histórica con reservas
        actividad.setActiva(false);
        actividadRepository.save(actividad);
    }
}
