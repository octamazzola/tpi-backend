package ar.edu.utn.frc.bda.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Usuario autenticado reconstruido a partir de los headers que propaga el api-gateway
 * (ver {@link UserHeaders}). Equivale al "SecurityUtils" descripto en el README.
 *
 * @param id       claim sub (UUID de Keycloak)
 * @param username claim preferred_username (ej. turista1)
 * @param email    claim email (puede ser null si el usuario no tiene email cargado)
 * @param roles    roles de realm_access.roles en mayúsculas
 */
public record UsuarioAutenticado(String id, String username, String email, Set<String> roles) {

    /**
     * Lee la identidad de los headers. Devuelve vacío si la petición no pasó por el gateway
     * (por ejemplo, una prueba local directa contra el puerto del microservicio).
     */
    public static Optional<UsuarioAutenticado> desde(HttpServletRequest request) {
        String username = request.getHeader(UserHeaders.USERNAME);
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String rolesHeader = request.getHeader(UserHeaders.ROLES);
        Set<String> roles = rolesHeader == null ? Set.of() : Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());

        String email = request.getHeader(UserHeaders.EMAIL);
        return Optional.of(new UsuarioAutenticado(
                request.getHeader(UserHeaders.USER_ID),
                username,
                email == null || email.isBlank() ? null : email,
                roles));
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol.toUpperCase());
    }

    public boolean esAdmin() {
        return tieneRol("ADMIN");
    }

    /** OPERADOR y PROVEEDOR son sinónimos (igual que en el SecurityConfig del gateway). */
    public boolean esOperador() {
        return tieneRol("OPERADOR") || tieneRol("PROVEEDOR");
    }
}
