# 🔐 Guía Integral: Autenticación con Keycloak, Postman, Roles (RBAC) y Mapa de Endpoints
**Backend de Aplicaciones — UTN FRC (2026)**  
**Dominio:** Sistema de Gestión de Actividades Turísticas y Excursiones

---

## 📌 1. ¿Por qué usamos Postman y para qué sirve jwt.io?

### A. El rol de Postman en el Backend
En una arquitectura de microservicios, el backend se desarrolla y valida antes de que exista la interfaz visual (frontend en React, Angular o móvil).
* **Postman actúa como el "Cliente Simulado":** Asume el rol del turista, del operador o del administrador.
* Permite enviar peticiones HTTP (`GET`, `POST`, `PUT`, `DELETE`), configurar cuerpos JSON, inyectar encabezados (`Authorization: Bearer <token>`) y verificar los códigos de estado (`200 OK`, `201 Created`, `401 Unauthorized`, `403 Forbidden`).

### B. El rol de jwt.io (La "Lupa" de diagnóstico)
El token devuelto por Keycloak parece una cadena aleatoria de texto cifrado, pero en realidad es un **JSON Web Token (RFC 7519)** codificado en Base64Url y firmado digitalmente.
Al pegarlo en **[jwt.io](https://jwt.io)**, se desglosa en 3 secciones coloreadas:
1. **Header (Rojo):** Algoritmo de firma asimétrica (`RS256`) y tipo (`JWT`).
2. **Payload (Violeta):** Datos de identidad (*claims*):
   * `iss`: Emisor oficial (`http://localhost:8180/realms/tpi-realm`).
   * `preferred_username`: Usuario (`turista1`, `operador1`, `admin1`).
   * `email`: Correo electrónico del usuario.
   * `realm_access.roles`: Lista de roles otorgados (`TURISTA`, `OPERADOR`, `ADMIN`).
   * `exp`: Fecha y segundo exacto en que caduca el token.
3. **Signature (Celeste):** Sello criptográfico generado con la clave privada de Keycloak. El API Gateway utiliza la clave pública de Keycloak para verificar que nadie haya alterado el contenido del token.

---

## 🔄 2. Comparativa de Enfoques: ¿Por qué este flujo y no el de redirección web?

| Característica | Enfoque A: Direct Access Grants (El que usamos hoy) | Enfoque B: Authorization Code Flow (El que recordabas con la web) |
|---|---|---|
| **¿Cómo funciona?** | Se envían `username` y `password` directamente en el `POST` a Keycloak y se recibe el token de inmediato. | Te redirige a una pantalla web de Keycloak/Google, te logueás, te da un `code` temporal y la web lo cambia por el token. |
| **Casos de uso** | Ideal para pruebas en **Postman**, scripts automatizados y comunicación entre servicios. | Obligatorio para **Frontends en producción** (React/Angular) para no manipular contraseñas en texto plano. |
| **Complejidad** | Muy baja (1 sola llamada HTTP). | Alta (requiere callbacks, PKCE y servidor frontend con redirecciones). |

---

## 👥 3. Matriz de Roles y Permisos (RBAC - Role Based Access Control)

Para cumplir con la consigna del TPI, el sistema define **3 roles de negocio bien diferenciados**:

| Rol | Actor de Negocio | Capacidades y Alcance |
|---|---|---|
| **`TURISTA`** | Cliente final que busca viajar | Explorar actividades públicas, consultar distancias y tiempos de traslado, crear sus propias reservas y cancelarlas. |
| **`OPERADOR`** | Guía o prestador turístico local | Publicar excursiones, editar cupos, puntos de encuentro y tarifas. Consultar contingentes y confirmar reservas. |
| **`ADMIN`** | Administrador de la plataforma | Gestión total: altas y bajas de categorías maestras, auditoría, anulación forzada y métricas del sistema. |

---

## 🌐 4. Mapa Completo de Endpoints por Microservicio

Todas las peticiones externas ingresan a través del **API Gateway (`http://localhost:8080`)**:

### 🧭 4.1. Microservicio de Catálogo (`ms-catalogo` / Puerto 8081)
Ruta Gateway: `/api/v1/catalogo/...`

| Método | Endpoint Gateway | Rol Requerido | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/catalogo/actividades` | **Público (Sin Token)** | Lista excursiones con filtros opcionales (`categoriaId`, `dificultad`, `precioMax`). |
| `GET` | `/api/v1/catalogo/actividades/{id}` | **Público (Sin Token)** | Detalle completo de una actividad (coordenadas, cupo, descripción). |
| `GET` | `/api/v1/catalogo/actividades/{id}/disponibilidad` | **Público / Interno** | Valida si hay cupos libres (consumido por reservas). |
| `GET` | `/api/v1/catalogo/categorias` | **Público (Sin Token)** | Lista todas las categorías (Trekking, Náutica, etc.). |
| `POST` | `/api/v1/catalogo/actividades` | `OPERADOR`, `ADMIN` | Da de alta una nueva excursión. |
| `PUT` | `/api/v1/catalogo/actividades/{id}` | `OPERADOR`, `ADMIN` | Modificación integral de datos y cupos. |
| `PATCH` | `/api/v1/catalogo/actividades/{id}` | `OPERADOR`, `ADMIN` | Modificación parcial de campos específicos. |
| `DELETE`| `/api/v1/catalogo/actividades/{id}` | `ADMIN` | Baja lógica (`activa = false`) para preservar historial. |
| `POST` | `/api/v1/catalogo/categorias` | `ADMIN` | Crea una nueva categoría maestra. |
| `DELETE`| `/api/v1/catalogo/categorias/{id}` | `ADMIN` | Elimina una categoría si no tiene actividades vinculadas. |

---

### 🎫 4.2. Microservicio de Reservas (`ms-reservas` / Puerto 8082)
Ruta Gateway: `/api/v1/reservas/...`

| Método | Endpoint Gateway | Rol Requerido | Descripción |
|---|---|---|---|
| `POST` | `/api/v1/reservas` | `TURISTA` | **Orquestación principal:** valida actividad en catálogo, calcula viaje con OSRM y chequea clima en Open-Meteo. |
| `GET` | `/api/v1/reservas/mis-reservas` | `TURISTA` | Lista las reservas del usuario extraído del token JWT (`sub` / `username`). |
| `GET` | `/api/v1/reservas/{id}` | `TURISTA`, `OPERADOR` | Detalle de la reserva con itinerario de viaje y pronóstico meteorológico. |
| `GET` | `/api/v1/reservas/actividad/{actividadId}` | `OPERADOR`, `ADMIN` | Lista contingente de personas anotadas a una salida. |
| `PUT` | `/api/v1/reservas/{id}/confirmar` | `OPERADOR`, `ADMIN` | Confirma la reserva y dispara mail de confirmación vía Mailpit. |
| `PUT` | `/api/v1/reservas/{id}/cancelar` | `TURISTA`, `ADMIN` | Cancela la reserva y dispara notificación vía Mailpit. |

---

### 🗺️ 4.3. Microservicio Geoespacial (`geoapi` / OSRM / Puerto 8083)
Ruta Gateway: `/api/v1/geo/...`

| Método | Endpoint Gateway | Rol Requerido | Descripción |
|---|---|---|---|
| `POST` | `/api/v1/geo/rutas/distancia` | Autenticado | Calcula distancia exacta en kilómetros y tiempo estimado de viaje entre origen y destino. |
| `GET` | `/api/v1/geo/rutas/estimacion` | Autenticado | Cálculo rápido por QueryParams (`?latOrig=...&lonOrig=...&latDest=...&lonDest=...`). |
| `GET` | `/api/v1/geo/health-osrm` | `ADMIN` | Chequeo de estado del contenedor Docker OSRM con el mapa de Argentina. |

---

## ⚙️ 5. Implementación del RBAC en Spring Cloud Gateway

### A. La Trampa de `.authenticated()` vs `.hasRole(...)`
* Por defecto, configurar `.pathMatchers(HttpMethod.POST, "/api/v1/catalogo/**").authenticated()` **no es suficiente**: solo verifica que la petición traiga un token JWT válido y firmado.
* Por eso, si un `TURISTA` enviaba un token válido, el sistema lo dejaba crear actividades aunque no fuera su función.

### B. El Desafío del Formato de Keycloak
* Spring Security busca las autoridades de usuario por defecto en el claim `scope` (ej. `SCOPE_email`, `SCOPE_profile`).
* **Keycloak**, en cambio, estructura los roles dentro de un objeto anidado en el JWT:
  ```json
  "realm_access": {
    "roles": [
      "default-roles-tpi-realm",
      "TURISTA",    // o "OPERADOR", o "ADMIN"
      "offline_access"
    ]
  }
  ```

### C. La Solución: `KeycloakGrantedAuthoritiesExtractor`
En [`SecurityConfig.java`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/api-gateway/src/main/java/api_gateway/config/SecurityConfig.java) se implementó un conversor reactivo que:
1. Inspecciona el claim `realm_access.roles`.
2. Mapea cada rol al formato estándar de Spring Security con prefijo `ROLE_` (`ROLE_TURISTA`, `ROLE_OPERADOR`, `ROLE_ADMIN`).
3. Aplica autorización granular por ruta y método HTTP:
   * `POST/PUT/PATCH /api/v1/catalogo/actividades/**` $\rightarrow$ `.hasAnyRole("OPERADOR", "PROVEEDOR", "ADMIN")`
   * `DELETE /api/v1/catalogo/actividades/**` $\rightarrow$ `.hasRole("ADMIN")`
   * `POST/DELETE /api/v1/catalogo/categorias/**` $\rightarrow$ `.hasRole("ADMIN")`
   * `POST /api/v1/reservas/**` $\rightarrow$ `.hasAnyRole("TURISTA", "ADMIN")`

### D. Comportamiento HTTP Verificado
* **Sin Token:** `401 Unauthorized` (El Gateway no deja pasar la petición).
* **Token de Turista creando Actividad:** `403 Forbidden` (`insufficient_scope`: el usuario está autenticado pero no tiene el rol requerido).
* **Token de Operador creando Actividad:** `201 Created` (Autorizado).

---

## 🛠️ 6. Pasos para Configurar los Roles en Keycloak

1. **Crear los Roles:**
   * En Keycloak (`http://localhost:8180` en realm `tpi-realm`), andá a **Realm roles** $\rightarrow$ **Create role**.
   * Roles requeridos: `TURISTA`, `OPERADOR`, `ADMIN`.
2. **Asignar Roles a los Usuarios:**
   * Vas a **Users** $\rightarrow$ seleccionás el usuario.
   * Pestaña **Role mapping** $\rightarrow$ clic en **Assign role** $\rightarrow$ seleccionás el rol $\rightarrow$ **Assign**.
   * Usuarios creados y verificados:
     * `turista1` (pass: `1234`) con rol `TURISTA`.
     * `operador1` (pass: `1234`) con rol `OPERADOR`.
     * `admin1` (pass: `1234`) con rol `ADMIN`.
3. **Comprobar en jwt.io:**
   * Al pedir el token y pegarlo en jwt.io, en `"realm_access": { "roles": [...] }` figura el rol correspondiente.

---

## 💡 7. Respuestas Clave para la Defensa Oral

* **¿Por qué la autenticación se hace en el Gateway y no en cada microservicio?**  
  > *"Para aplicar el principio de responsabilidad única (Single Responsibility) y patrón Reverse Proxy. El Gateway autentica y valida el token una sola vez en el borde del sistema, liberando a los microservicios internos de esa carga repetitiva."*
* **¿Cuál es la diferencia entre un código 401 y un 403?**  
  > *"401 Unauthorized significa 'no sé quién sos' (falta el token o está vencido/mal firmado). 403 Forbidden significa 'sé perfectamente quién sos, pero tu rol (ej. Turista) no tiene permiso para realizar esta acción (ej. crear una excursión)'."*
* **¿Cómo mapea Spring Security los roles que envía Keycloak en el token?**  
  > *"Keycloak emite los roles dentro del claim 'realm_access.roles'. En el SecurityConfig del Gateway implementamos un conversor que extrae esa lista de roles y los transforma en objetos GrantedAuthority con prefijo 'ROLE_', permitiendo usar métodos como hasRole('OPERADOR') o hasRole('ADMIN')."*
* **¿Cómo sabe el microservicio de reservas quién es el turista sin pedir el ID por JSON?**  
  > *"El Gateway extrae el token JWT validado y le propaga al microservicio los encabezados HTTP con el `sub` (UUID del usuario) y su rol, evitando la falsificación de identidad."*
* **¿Qué sucede si Keycloak se cae temporalmente?**  
  > *"El Gateway ya descargó en su caché local las claves públicas (JWKS). Por lo tanto, puede seguir validando tokens JWT existentes sin consultar a Keycloak hasta que dichos tokens expiren, garantizando alta disponibilidad."*

