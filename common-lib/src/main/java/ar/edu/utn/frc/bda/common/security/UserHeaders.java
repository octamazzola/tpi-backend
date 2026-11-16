package ar.edu.utn.frc.bda.common.security;

/**
 * Contrato de headers HTTP con los que el api-gateway propaga la identidad del usuario
 * (extraída del token JWT de Keycloak ya validado) hacia los microservicios internos.
 *
 * El gateway SIEMPRE elimina estos headers de la petición entrante antes de agregarlos,
 * para que un cliente no pueda falsificar su identidad enviándolos a mano.
 */
public final class UserHeaders {

    /** Claim {@code sub}: UUID del usuario en Keycloak. */
    public static final String USER_ID = "X-User-Id";

    /** Claim {@code preferred_username}: nombre de usuario (ej. turista1). */
    public static final String USERNAME = "X-User-Name";

    /** Claim {@code email}. */
    public static final String EMAIL = "X-User-Email";

    /** Roles de {@code realm_access.roles}, separados por coma (ej. TURISTA,offline_access). */
    public static final String ROLES = "X-User-Roles";

    private UserHeaders() {
    }
}
