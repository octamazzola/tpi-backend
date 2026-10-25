package ms_catalogo.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ms_catalogo.entities.Actividad;
import ms_catalogo.entities.Dificultad;

@Repository
public interface ActividadRepository extends JpaRepository<Actividad, Long> {

    // 1. Obtener todas las actividades que están activas (no dadas de baja lógica)
    List<Actividad> findByActivaTrue();

    // 2. Buscar una actividad por ID solo si está activa
    Optional<Actividad> findByIdAndActivaTrue(Long id);

    // 3. Filtrar actividades activas pertenecientes a una categoría específica
    List<Actividad> findByCategoriaIdAndActivaTrue(Long categoriaId);

    // 4. Filtrar actividades activas según su nivel de dificultad
    List<Actividad> findByDificultadAndActivaTrue(Dificultad dificultad);
}
