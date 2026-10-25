package ms_catalogo.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ms_catalogo.entities.Categoria;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    // Método derivado: Spring genera el SQL "SELECT COUNT(*) > 0 FROM categorias
    // WHERE LOWER(nombre) = LOWER(?)"

    boolean existsByNombreIgnoreCase(String nombre);

    // Devuelve true si existe al menos un registro cuyo nombre coincide con el
    // parámetro (sin distinguir mayúsculas),
    // y false si no hay ninguno.

    Optional<Categoria> findByNombreIgnoreCase(String nombre);
    // Optional<T> es un contenedor de Java que puede tener un valor o estar vacío.
    // Se usa para evitar devolver null y los NullPointerException.
    // Te obliga a decidir qué hacer cuando no hay resultado, en vez de olvidarte de
    // chequear null.
}