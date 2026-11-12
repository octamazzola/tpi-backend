# TPI Backend - Sistema de Gestión de Actividades Turísticas y Excursiones
**Backend de Aplicaciones — UTN FRC (2026)** · Dominio 5: *Gestión de actividades turísticas y excursiones*

Plataforma backend basada en microservicios que administra las actividades que publican prestadores turísticos (trekking, cabalgatas, navegación) y las reservas de los turistas. No se limita a registrar reservas: **decide si una actividad puede realizarse y si una reserva sigue siendo viable** según cupo, edad de los participantes, temporada y clima.

---

## 🏗️ 1. Arquitectura

```
                          ┌──────────── Docker network: tpi-network (trusted zone) ────────────┐
[Turista / Operador /     │                                                                     │
 Admin  (Postman)] ──8080──► api-gateway ──► ms-catalogo ──► PostgreSQL (catalogodb)          │
        │                 │   JWT + RBAC    ms-reservas ──► PostgreSQL (reservasdb)            │
        │                 │   X-User-*        │  ├──► ms-catalogo                               │
        └──8180──► Keycloak                   │  ├──► geoapi ──► OSRM (mapa de Argentina)       │
                          │                   │  ├──► Mailpit (SMTP) ──8025──► bandeja web      │
                          └───────────────────┼──┼──────────────────────────────────────────────┘
                                              └──► Open-Meteo (API pública externa, Internet)
```

| Componente | Responsabilidad |
|---|---|
| **`api-gateway`** | Único punto de entrada. Enrutamiento, Security Filter (OAuth2 Resource Server + RBAC por rol), propagación de identidad y Swagger UI agrupado. |
| **`ms-catalogo`** | Actividades y categorías: cupo, precio, punto de encuentro, dificultad, restricciones de edad, temporada y dependencia del clima. Baja lógica. |
| **`ms-reservas`** | Orquestador: reservas con participantes, reglas de negocio, revalidación programada y notificaciones. |
| **`geoapi`** | Distancias y tiempos de traslado sobre **OSRM** (Open Source Routing Machine). |
| **`common-lib`** | JAR Maven compartido por los microservicios (ver sección 6). |
| **Keycloak** | Identity Provider OIDC: tokens JWT con roles `TURISTA`, `OPERADOR`, `ADMIN`. |
| **Open-Meteo** | API pública externa: pronóstico que decide si una excursión al aire libre es viable. |

**Persistencia:** Database-per-Service sobre una instancia de PostgreSQL 16 (`catalogodb`, `reservasdb`). `actividadId` en reservas es una referencia lógica validada por API, no una FK física.

---

## 👥 2. Roles

| Rol | Puede |
|---|---|
| Anónimo | Consultar el catálogo y las categorías. |
| `TURISTA` | Crear reservas, ver y cancelar **solo las propias**, consultar traslados. |
| `OPERADOR` | Publicar y modificar actividades, ver el contingente de una actividad, confirmar reservas. |
| `ADMIN` | Todo lo anterior + categorías, baja lógica, auditoría de reservas, revalidación manual y health check de OSRM. |

Detalle completo en [ROLES-Y-PERMISOS-RBAC.md](ROLES-Y-PERMISOS-RBAC.md).

---

## 🌐 3. Endpoints (todos vía `http://localhost:8080`)

### Catálogo — `/api/v1/catalogo`
| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| `GET` | `/actividades` | Público | Lista actividades activas. Filtros: `categoriaId`, `dificultad`, `precioMax`, `ciudad` (parcial, sin distinguir mayúsculas ni tildes). Sin coincidencias responde `200` con `[]`. |
| `GET` | `/actividades/{id}` | Público | Detalle (punto de encuentro, cupo, edad, temporada, clima). |
| `GET` | `/actividades/{id}/disponibilidad` | Público | Si la actividad está activa y tiene cupo. |
| `POST` | `/actividades` | `OPERADOR`, `ADMIN` | Publica una actividad. |
| `PUT` / `PATCH` | `/actividades/{id}` | `OPERADOR`, `ADMIN` | Reemplazo completo / ajuste parcial. |
| `DELETE` | `/actividades/{id}` | `ADMIN` | Baja lógica (`activa = false`). |
| `GET` | `/categorias`, `/categorias/{id}` | Público | Categorías. |
| `POST` | `/categorias` | `ADMIN` | Crea una categoría. |
| `DELETE` | `/categorias/{id}` | `ADMIN` | Elimina si no tiene actividades (`409` si tiene). |

### Reservas — `/api/v1/reservas`
| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| `POST` | `/` | `TURISTA`, `ADMIN` | Crea una reserva con sus participantes (proceso principal). |
| `GET` | `/` | `ADMIN` | Todas las reservas. |
| `GET` | `/mis-reservas` | `TURISTA`, `ADMIN` | Reservas del usuario del token. |
| `GET` | `/{id}` | Autenticado | Detalle (un turista solo ve las propias). |
| `GET` | `/actividad/{actividadId}` | `OPERADOR`, `ADMIN` | Contingente de una excursión. |
| `PUT` | `/{id}/confirmar` | `OPERADOR`, `ADMIN` | Confirma y envía el voucher por email. |
| `PUT` | `/{id}/cancelar` | `TURISTA`, `ADMIN` | Cancela, libera el cupo y notifica. |
| `POST` | `/revalidaciones` | `ADMIN` | Ejecuta la revalidación de reservas en el momento. |

### GeoAPI — `/api/v1/geo`
| Método | Endpoint | Rol | Descripción |
|---|---|---|---|
| `POST` | `/rutas/distancia` | Autenticado | Distancia y duración entre `origen` y `destino` (`{ latitud, longitud }`). |
| `GET` | `/rutas/estimacion` | Autenticado | Lo mismo por query params (`latOrig`, `lonOrig`, `latDest`, `lonDest`). |
| `GET` | `/health-osrm` | `ADMIN` | Estado del motor OSRM. |

### Documentación OpenAPI
**Swagger UI agrupado:** http://localhost:8080/swagger-ui.html (desplegable con Catálogo, Reservas y GeoAPI). El botón *Authorize* acepta el token JWT de Keycloak.

---

## 🔄 4. Proceso principal y reglas de negocio

`POST /api/v1/reservas` (turista autenticado):
1. El **Gateway** valida el JWT y el rol, y propaga la identidad en headers `X-User-*` (el turista no puede falsificarla).
2. **ms-reservas → ms-catalogo:** la actividad existe y está activa (`404`).
3. **Reglas locales:** temporada de la actividad y edad de cada participante el día de la excursión (`400`).
4. **RN01 cupo:** participantes ya reservados para la fecha + nuevos ≤ cupo (`409`).
5. **RN02 clima → Open-Meteo:** si la actividad requiere buen clima y llueve > 70 % o hay tormenta, la reserva queda `RECHAZADA_POR_CLIMA`.
6. **RN03 traslado → geoapi/OSRM:** distancia y duración desde el origen del turista.
7. Se guarda en `PENDIENTE` y se envía un email (Mailpit). Respuesta `201 Created`.

**Revalidación (cada hora y a pedido del admin):** una reserva válida puede dejar de serlo. Se cancelan las reservas de actividades dadas de baja, fuera de la nueva temporada o que exceden un cupo reducido; se rechazan por clima si el pronóstico empeora; y se aplica la **regla de 48 hs** (recordatorio a 48 hs, anulación a 24 hs si sigue pendiente). Cada cambio se notifica por email.

**Fallas de componentes:** Open-Meteo o geoapi caídos → la reserva continúa sin alerta / sin traslado. SMTP caído → no afecta la reserva. Catálogo caído → `502` al crear; la revalidación no toca esas reservas.

Detalle en [ms-reservas/README.md](ms-reservas/README.md).

---

## 🔐 5. Seguridad y trusted zone

* **Keycloak** emite JWT (OIDC). El **Gateway** es OAuth2 Resource Server: valida la firma contra el JWKS, mapea `realm_access.roles` a `ROLE_*` y aplica RBAC por ruta y método (`401` sin token o token inválido, `403` sin rol).
* **Propagación de identidad:** el filtro `UserHeadersFilter` del Gateway elimina cualquier `X-User-*` entrante y los vuelve a armar desde el token. `ms-reservas` usa esa identidad para asignar el turista y controlar que solo acceda a sus reservas.
* **Trusted zone:** en Docker solo se publican el Gateway (`8080`), Keycloak (`8180`), la bandeja web de Mailpit (`8025`) y Postgres en `127.0.0.1:5432`. Los microservicios, OSRM y el SMTP solo son accesibles dentro de `tpi-network`, así que nadie puede saltearse el Gateway.

---

## 📦 6. Componente común (`common-lib`)

JAR Maven (`ar.edu.utn.frc.bda:common-lib`) instalado en `.m2` y usado por `ms-catalogo`, `ms-reservas` y `api-gateway`:
* `exceptions.GlobalExceptionHandler` (auto-configurado): respuestas uniformes `400`, `403`, `404`, `405`, `409`, `502`, `500`.
* `exceptions.*`: `BadRequestException`, `ResourceNotFoundException`, `ConflictException`, `ForbiddenException`.
* `dtos.ErrorResponseDTO`: `{ "timestamp", "status", "error", "message", "path", "validaciones" }`.
* `security.UserHeaders` / `security.UsuarioAutenticado`: contrato de identidad entre el Gateway y los microservicios.
* `config.CommonOpenApiAutoConfiguration`: metadatos OpenAPI y esquema Bearer JWT para Swagger.

Quedan **fuera** a propósito las entidades, repositorios y reglas de negocio: cada microservicio conserva su modelo y su base.

---

## 🚀 7. Ejecución

### Paso 1: Datos del mapa OSRM
> ⚠️ Por su tamaño (+4 GB) los datos preprocesados del mapa no se versionan.
1. Descargá el archivo desde el enlace del equipo: 👉 **[osrm-data (Google Drive)](https://drive.google.com/file/d/1KaBt7SngvWpoHdeLkjdckU9Nw5lSn9Yi/view?usp=sharing)**
2. Descomprimilo en la raíz del repositorio, en la carpeta `osrm-data/`.

Sin estos datos todo funciona igual, salvo el cálculo de traslados (las reservas quedan con traslado "No disponible").

### Paso 2: Levantar todo
```powershell
docker compose up --build -d
```
Los Dockerfiles son multi-stage: **compilan cada módulo dentro del contenedor**, no hace falta Maven en la máquina. El primer build tarda unos minutos (descarga dependencias); los siguientes usan caché.

* Keycloak importa `keycloak/tpi-realm.json`: realm `tpi-realm`, cliente `tpi-client`, usuarios `turista1`, `turista2`, `operador1`, `admin1` (contraseña `1234`).
* Postgres crea `reservasdb` con `init-db/01-init.sql` **solo la primera vez** que se crea el volumen. Si venías de una versión anterior, recrealo una vez con `docker compose down -v`.

| URL | Qué es |
|---|---|
| http://localhost:8080 | API Gateway |
| http://localhost:8080/swagger-ui.html | Swagger UI agrupado |
| http://localhost:8180 | Keycloak (admin / admin) |
| http://localhost:8025 | Bandeja de correos (Mailpit) |

### Paso 3: Probar
Importá [`tpi_postman_collection.json`](tpi_postman_collection.json) en Postman y corré la colección en orden con el *Collection Runner*: obtiene los tokens solo, encadena IDs y calcula las fechas.

### Alternativa: desarrollo sin Docker
```powershell
mvn clean install -DskipTests
docker compose -f docker-compose.osrm.yml up -d   # solo OSRM; no usar junto con el compose completo
mvn -pl ms-catalogo spring-boot:run               # cada uno en su terminal
mvn -pl geoapi spring-boot:run
mvn -pl ms-reservas spring-boot:run
mvn -pl api-gateway spring-boot:run
```
Sin Docker los servicios usan H2 en memoria. Keycloak se puede levantar solo con `docker compose up -d keycloak`. El SMTP de Mailpit no se publica fuera de Docker, así que en este modo los correos fallan (solo se registra en el log; la reserva no se ve afectada).

### Tests
```powershell
mvn test
```
