# 🛡️ Especificación Oficial de Seguridad y Roles (RBAC)
**Sistema de Gestión de Actividades Turísticas y Excursiones**  
**Cátedra:** Backend de Aplicaciones — UTN FRC (2026)  
**Arquitectura:** Microservicios con Spring Cloud Gateway, Keycloak (OAuth2 / OIDC) y Base de Datos PostgreSQL.

---

## 📌 1. Visión General de la Seguridad

El sistema implementa una arquitectura de seguridad **Zero-Trust en el borde (Edge Security)** basada en tokens JWT (*JSON Web Tokens*) stateless emitidos por **Keycloak**:

```
[ Cliente / Postman ] 
        │
        ├── (1) POST /protocol/openid-connect/token ──► [ Keycloak :8180 ]
        │   ◄── Devuelve Access Token (JWT) ────────────┘
        │
        ▼ (2) HTTP + Authorization: Bearer <token>
[ API Gateway :8080 ] ──► Valida firma JWT contra JWKS en memoria
        │
        ├──► ¿Tiene rol / permisos necesarios?
        │       ├── NO ──► 401 Unauthorized / 403 Forbidden
        │       └── SÍ ──► Rutea al Microservicio correspondiente
        ▼
┌───────────────────┬───────────────────┬───────────────────┐
│ ms-catalogo :8081 │ ms-reservas :8082 │   geoapi :8083    │
└───────────────────┴───────────────────┴───────────────────┘
```

---

## 👥 2. Definición Formal de Actores y Roles

### 1. `ANÓNIMO` (Visitante Público)
* **¿Quién es?** Cualquier persona que navega por la plataforma web o aplicación móvil sin registrarse.
* **Propósito:** Descubrir actividades turísticas y consultar información para decidir una compra.
* **Qué puede hacer:**
  * Ver el catálogo completo de excursiones.
  * Aplicar filtros de búsqueda por dificultad (`BAJA`, `MEDIA`, `ALTA`, `EXPERTO`), categoría y precio máximo.
  * Ver el detalle técnico de una actividad (coordenadas GPS de encuentro, itinerario, equipamiento sugerido).
  * Consultar si quedan cupos disponibles para una fecha o actividad.
  * Listar las categorías existentes.
* **Qué NO puede hacer:** No puede reservar, no puede publicar actividades y no puede alterar datos.

---

### 2. `TURISTA`
* **¿Quién es?** El cliente final autenticado que desea contratar y disfrutar las excursiones.
* **Propósito:** Reservar salidas turísticas, planificar su viaje y gestionar sus reservas.
* **Qué puede hacer:**
  * Crear reservas para una excursión indicando la cantidad de personas y su **punto de partida** (ej. su hotel en la ciudad). Esto dispara la orquestación automática de cálculo de ruta con OSRM y validación de pronóstico del tiempo con Open-Meteo.
  * Consultar su historial personal de reservas (**"Mis Reservas"**).
  * Consultar el detalle de su reserva (itinerario de llegada en auto y reporte meteorológico).
  * Cancelar su reserva previa con anticipación.
* **Qué NO puede hacer:**
  * No puede ver las reservas de otros turistas (privacidad y protección de datos personales).
  * No puede crear, editar o borrar actividades en el catálogo.
  * No puede modificar categorías ni acceder al health check del sistema.

---

### 3. `PROVEEDOR` / `OPERADOR`
* **¿Quién es?** El prestador de servicios turísticos (guía de montaña, agencia de turismo aventura, coordinador local).
* **Propósito:** Ofrecer sus servicios, administrar la capacidad de sus excursiones y gestionar la lista de inscriptos.
* **Qué puede hacer:**
  * Publicar nuevas actividades turísticas en el catálogo (`POST /actividades`).
  * Modificar o actualizar sus actividades existentes: punto de encuentro, precio base, cupo máximo o requerimientos climáticos (`PUT` / `PATCH`).
  * Consultar el listado de reservas y pasajeros confirmados de sus actividades para armar las listas de asistencia.
  * Confirmar reservas pendientes (`PUT /confirmar`), lo que automáticamente emite el correo de confirmación al turista vía Mailpit.
* **Qué NO puede hacer:**
  * No puede realizar bajas definitivas o lógicas no supervisadas.
  * No puede crear ni eliminar categorías temáticas maestras (para evitar proliferación de categorías redundantes o desorden en el catálogo global).
  * No puede ver reservas de actividades de otros prestadores.

---

### 4. `ADMIN` (Administrador General)
* **¿Quién es?** El administrador o dueño de la plataforma turística.
* **Propósito:** Garantizar el orden del sistema, auditar operaciones, supervisar categorías y monitorear la infraestructura.
* **Qué puede hacer:**
  * Gestionar el catálogo maestro de categorías (`POST` y `DELETE` sobre `/categorias`).
  * Realizar la **baja lógica** (`DELETE /actividades/{id}`, marcando `activa = false`) para suspender excursiones sin romper la integridad referencial de reservas históricas.
  * Auditoría total: consultar todas las reservas del sistema sin importar qué turista o prestador las originó.
  * Monitorear la salud y métricas de los microservicios y del motor geoespacial OSRM (`/health-osrm`, `/actuator`).

---

## 📊 3. Matriz Exhaustiva de Endpoints y Permisos

| Microservicio | Método | Endpoint Gateway | Anónimo | TURISTA | PROVEEDOR | ADMIN | Justificación y Regla de Negocio |
| :--- | :---: | :--- | :---: | :---: | :---: | :---: | :--- |
| **ms-catalogo** | `GET` | `/api/v1/catalogo/actividades` | ✅ | ✅ | ✅ | ✅ | Búsqueda pública abierta con filtros combinados. |
| **ms-catalogo** | `GET` | `/api/v1/catalogo/actividades/{id}` | ✅ | ✅ | ✅ | ✅ | Ficha técnica y coordenadas de la actividad. |
| **ms-catalogo** | `GET` | `/api/v1/catalogo/actividades/{id}/disponibilidad` | ✅ | ✅ | ✅ | ✅ | Endpoint liviano de consulta de cupos libres. |
| **ms-catalogo** | `GET` | `/api/v1/catalogo/categorias` | ✅ | ✅ | ✅ | ✅ | Listado público de categorías para navegación web. |
| **ms-catalogo** | `POST` | `/api/v1/catalogo/actividades` | ❌ | ❌ | ✅ | ✅ | Alta de excursión. Solo prestadores y administradores. |
| **ms-catalogo** | `PUT` | `/api/v1/catalogo/actividades/{id}` | ❌ | ❌ | ✅ | ✅ | Reemplazo completo de datos de la excursión. |
| **ms-catalogo** | `PATCH` | `/api/v1/catalogo/actividades/{id}` | ❌ | ❌ | ✅ | ✅ | Actualización parcial rápida (precios y cupos). |
| **ms-catalogo** | `DELETE` | `/api/v1/catalogo/actividades/{id}` | ❌ | ❌ | ❌ | ✅ | **Baja lógica** (`activa = false`). Exclusivo ADMIN. |
| **ms-catalogo** | `POST` | `/api/v1/catalogo/categorias` | ❌ | ❌ | ❌ | ✅ | Creación de categorías taxonómicas del sistema. |
| **ms-catalogo** | `DELETE` | `/api/v1/catalogo/categorias/{id}` | ❌ | ❌ | ❌ | ✅ | Eliminación de categoría sin actividades asociadas. |
| **ms-reservas** | `POST` | `/api/v1/reservas` | ❌ | ✅ | ❌ | ✅ | Orquestación completa de reserva (Catálogo + OSRM + Clima). |
| **ms-reservas** | `GET` | `/api/v1/reservas/mis-reservas` | ❌ | ✅ | ❌ | ❌ | Historial propio del turista obtenido del claim del JWT. |
| **ms-reservas** | `GET` | `/api/v1/reservas/{id}` | ❌ | ✅ *(Propia)* | ✅ *(Propia)* | ✅ *(Todas)* | Detalle con itinerario vial y reporte meteorológico. |
| **ms-reservas** | `GET` | `/api/v1/reservas/actividad/{id}` | ❌ | ❌ | ✅ | ✅ | Listado de inscriptos para el guía u operador. |
| **ms-reservas** | `PUT` | `/api/v1/reservas/{id}/confirmar` | ❌ | ❌ | ✅ | ✅ | Confirma la reserva y emite voucher vía Mailpit. |
| **ms-reservas** | `PUT` | `/api/v1/reservas/{id}/cancelar` | ❌ | ✅ *(Propia)* | ❌ | ✅ | Cancelación voluntaria y liberación de cupos. |
| **ms-reservas** | `/api/v1/reservas` | `GET` | ❌ | ❌ | ❌ | ✅ | Auditoría y control global de reservas del sistema. |
| **geoapi** | `POST` | `/api/v1/geo/rutas/distancia` | ❌ | ✅ | ✅ | ✅ | Cálculo terrestre de distancia (km) y tiempo (min). |
| **geoapi** | `GET` | `/api/v1/geo/rutas/estimacion` | ❌ | ✅ | ✅ | ✅ | Estimación rápida de viaje mediante parámetros URL. |
| **geoapi** | `GET` | `/api/v1/geo/health-osrm` | ❌ | ❌ | ❌ | ✅ | Monitoreo del contenedor Docker del motor OSRM. |

---

## 🔒 4. Manejo de Errores y Códigos de Respuesta HTTP

Cuando un usuario interactúa con la API, el sistema responde siguiendo estrictamente los estándares RFC:

* **`200 OK` / `201 Created`**: La petición fue autorizada y ejecutada exitosamente.
* **`400 Bad Request`**: Error de validación sintáctica en el DTO (ej. precio negativo, cupo menor a 1, fecha pasada).
* **`401 Unauthorized`**: La petición no incluye la cabecera `Authorization: Bearer <token>` o el token está vencido / mal firmado. El API Gateway la rechaza antes de tocar ningún microservicio.
* **`403 Forbidden`**: El usuario está correctamente autenticado, pero su rol no tiene los privilegios necesarios (ej. un `TURISTA` intentando hacer `DELETE` a una categoría o un `PROVEEDOR` intentando crear una categoría).
* **`404 Not Found`**: El recurso solicitado (actividad, reserva o categoría) no existe.
* **`409 Conflict`**: Conflicto de negocio (ej. se intenta reservar pero no quedan cupos suficientes, o se intenta borrar una categoría que aún tiene actividades asociadas).

---

## 🧪 5. Estructura de Pruebas en Postman (`tpi_postman_collection.json`)

Para probar y defender esta arquitectura, se diseñó la colección con scripts automatizados de extracción de tokens:

### Paso 1: Obtener el Token deseado
En la carpeta **`0. Autenticación y Obtención de Tokens (Keycloak)`**:
* Ejecutar **`0.1 Iniciar Sesión como TURISTA`**: Autentica a `turista1` y guarda su token en la variable `{{token}}`.
* Ejecutar **`0.2 Iniciar Sesión como PROVEEDOR`**: Autentica a `operador1` y actualiza la variable `{{token}}`.
* Ejecutar **`0.3 Iniciar Sesión como ADMINISTRADOR`**: Autentica a `admin1` y actualiza la variable `{{token}}`.

### Paso 2: Ejecutar los Endpoints de Negocio
Todos los endpoints en las carpetas `1. Catálogo`, `2. Reservas` y `3. GeoAPI` utilizan automáticamente `{{token}}`. No es necesario copiar ni pegar cadenas de texto manualmente.

### Paso 3: Demostración de Seguridad en la Defensa
En la carpeta **`4. Pruebas de Seguridad RBAC`**:
* **4.1 Prueba Sin Token:** Demuestra que al intentar crear una excursión sin token, el Gateway responde inmediatamente `401 Unauthorized`.
* **4.2 Prueba Catálogo Público:** Demuestra que las consultas `GET` de catálogo funcionan sin autenticación (`200 OK`).
* **4.3 Prueba de Rol Inadecuado:** Demuestra que si un turista intenta borrar una categoría o crear actividades, el sistema protege la operación.

---

## 🎓 6. Preguntas Clave para la Defensa Oral del TPI

1. **¿Por qué la verificación de roles y JWT se hace en el API Gateway?**  
   * **Respuesta:** Para centralizar la seguridad en el borde (*Edge Server*). Si cada microservicio tuviera que implementar su propio filtro de seguridad contra Keycloak, tendríamos código duplicado, mayor latencia y acoplamiento innecesario. Los microservicios internos confían en el Gateway y procesan lógica pura de negocio.

2. **¿Cómo sabe el microservicio de reservas qué turista está realizando la compra?**  
   * **Respuesta:** El Gateway valida el token JWT emitido por Keycloak y extrae el identificador del usuario (`sub` o `preferred_username`). Este dato se propaga hacia el microservicio interno en una cabecera HTTP segura, evitando que el cliente pueda falsear su identidad en el cuerpo JSON.

3. **¿Por qué el borrado de actividades es una baja lógica y no física?**  
   * **Respuesta:** Para preservar la integridad referencial y el historial financiero y legal. Si elimináramos físicamente una actividad de la base de datos con `DELETE FROM actividades`, todas las reservas históricas asociadas quedarían huérfanas o fallarían por restricción de clave foránea (*Foreign Key*). La baja lógica (`activa = false`) oculta la excursión para nuevas reservas pero mantiene intacto el historial.
