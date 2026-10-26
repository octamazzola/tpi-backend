package ms_catalogo.services.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import ms_catalogo.dtos.CategoriaRequestDTO;
import ms_catalogo.dtos.CategoriaResponseDTO;
import ms_catalogo.entities.Categoria;
import ms_catalogo.exceptions.BadRequestException;
import ms_catalogo.exceptions.ResourceNotFoundException;
import ms_catalogo.mappers.CategoriaMapper;
import ms_catalogo.repositories.ActividadRepository;
import ms_catalogo.repositories.CategoriaRepository;
import ms_catalogo.services.CategoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ActividadRepository actividadRepository;
    private final CategoriaMapper categoriaMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> obtenerTodas() {
        return categoriaRepository.findAll().stream()
                .map(categoriaMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoriaResponseDTO obtenerPorId(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));
        return categoriaMapper.toResponseDTO(categoria);
    }

    @Override
    @Transactional
    public CategoriaResponseDTO crear(CategoriaRequestDTO dto) {
        if (categoriaRepository.existsByNombreIgnoreCase(dto.getNombre().trim())) {
            throw new BadRequestException("Ya existe una categoría registrada con el nombre: " + dto.getNombre());
        }

        Categoria categoria = categoriaMapper.toEntity(dto);
        categoria.setNombre(dto.getNombre().trim());
        Categoria guardada = categoriaRepository.save(categoria);

        return categoriaMapper.toResponseDTO(guardada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Categoría no encontrada con ID: " + id);
        }

        // Validación de Integridad Referencial: no se puede eliminar si hay actividades asociadas
        if (actividadRepository.existsByCategoriaId(id)) {
            throw new BadRequestException("No es posible eliminar la categoría con ID " + id + " porque tiene actividades turísticas vinculadas.");
        }

        categoriaRepository.deleteById(id);
    }
}
