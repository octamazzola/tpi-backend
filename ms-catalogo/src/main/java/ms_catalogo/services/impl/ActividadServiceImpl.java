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
import ar.edu.utn.frc.bda.common.exceptions.BadRequestException;
import ar.edu.utn.frc.bda.common.exceptions.ResourceNotFoundException;
import ms_catalogo.mappers.ActividadMapper;
import ms_catalogo.repositories.ActividadRepository;
import ms_catalogo.repositories.CategoriaRepository;
import ms_catalogo.services.ActividadService;
import ms_catalogo.services.TextoNormalizado;
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
    public List<ActividadResponseDTO> listarActividades(Long categoriaId, Dificultad dificultad, Double precioMax, String ciudad) {
        List<Actividad> actividades = actividadRepository.findByActivaTrue();

        // Aplicamos los filtros opcionales solicitados en el README
        return actividades.stream()
                .filter(a -> categoriaId == null || a.getCategoria().getId().equals(categoriaId))
                .filter(a -> dificultad == null || a.getDificultad().equals(dificultad))
                .filter(a -> precioMax == null || a.getPrecioBase() <= precioMax)
                // Coincidencia parcial sobre la ciudad, sin distinguir mayúsculas ni tildes:
                // "carlos paz", "Villa Carlos" y "VILLA CARLOS PAZ" encuentran "Villa Carlos Paz".
                .filter(a -> ciudad == null || ciudad.isBlank() || TextoNormalizado.contiene(a.getCiudad(), ciudad))
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

        validarRestricciones(dto.getEdadMinima(), dto.getEdadMaxima(), dto.getMesInicioTemporada(), dto.getMesFinTemporada());
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

        validarRestricciones(dto.getEdadMinima(), dto.getEdadMaxima(), dto.getMesInicioTemporada(), dto.getMesFinTemporada());
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
        if (dto.getCiudad() != null && !dto.getCiudad().isBlank()) {
            actividad.setCiudad(dto.getCiudad().trim());
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

        if (dto.getEdadMinima() != null) {
            actividad.setEdadMinima(dto.getEdadMinima());
        }
        if (dto.getEdadMaxima() != null) {
            actividad.setEdadMaxima(dto.getEdadMaxima());
        }
        if (dto.getMesInicioTemporada() != null) {
            actividad.setMesInicioTemporada(dto.getMesInicioTemporada());
        }
        if (dto.getMesFinTemporada() != null) {
            actividad.setMesFinTemporada(dto.getMesFinTemporada());
        }
        validarRestricciones(actividad.getEdadMinima(), actividad.getEdadMaxima(),
                actividad.getMesInicioTemporada(), actividad.getMesFinTemporada());

        Actividad actualizada = actividadRepository.save(actividad);
        return actividadMapper.toResponseDTO(actualizada);
    }

    /**
     * Reglas de consistencia entre campos: rango de edades coherente y temporada completa.
     */
    private void validarRestricciones(Integer edadMinima, Integer edadMaxima, Integer mesInicio, Integer mesFin) {
        if (edadMinima != null && edadMaxima != null && edadMinima > edadMaxima) {
            throw new BadRequestException("La edad mínima no puede ser mayor que la edad máxima");
        }
        if ((mesInicio == null) != (mesFin == null)) {
            throw new BadRequestException("La temporada debe indicar mes de inicio y mes de fin, o ninguno de los dos");
        }
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
