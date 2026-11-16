# 📝 Cambios en la rama `ms-reservas-impl`
**TPI Backend — Sistema de Gestión de Actividades Turísticas y Excursiones**

Este documento resume **qué cambió y por qué**, para que todo el equipo pueda entenderlo, probarlo y defenderlo.
Los cambios salen de revisar el repo contra los READMEs, contra el **enunciado del TPI** y contra **nuestro documento del proyecto** (el Google Doc con C1/C2, reglas de negocio y alcance).

---

## ⚡ Lo primero: qué tenés que hacer después de bajar la rama

1. **Borrar el volumen viejo de Postgres** (si no, no se crea `reservasdb` ni la tabla `participantes`):
   ```bash
   docker compose down -v
   ```
2. **Compilar con Maven y levantar todo:**
   ```bash
   mvn clean package -DskipTests
   docker compose up --build -d
   ```
3. **Reimportar Postman:** borrar la colección vieja e importar `tpi_postman_collection.json`. Correrla entera con el *Collection Runner*.
4. **Keycloak ya no se configura a mano:** el realm, el cliente, los roles y los usuarios se importan solos.

| URL | Qué es |
|---|---|
| http://localhost:8080 | API Gateway (única entrada a las APIs) |
| http://localhost:8180 | Keycloak (admin / admin) |
| http://localhost:8025 | Bandeja de correos (Mailpit) |

Usuarios de prueba (contraseña `1234`): `turista1`, `turista2`, `operador1`, `admin1`.

> ⚠️ Los puertos 8081, 8082 y 8083 **ya no se publican** en Docker (ver "Trusted zone"). Todo se prueba por el Gateway en el 8080.

---

## 🔴 1. Problemas críticos que se corrigieron

### 1.1. `geoapi` no estaba en Docker
- **Antes:** `docker-compose.yml` no tenía `geoapi` ni `osrm`, pero el gateway y `ms-reservas` apuntaban a `http://geoapi:8083`. En Docker, **todo `POST /reservas` daba 500**.
- **Ahora:** `osrm` y `geoapi` están en el compose principal, en la misma red. Además, si geoapi u OSRM se caen, la reserva **sigue igual** con el traslado en `"No disponible"` (fallback).
- **Por qué:** el enunciado pide un despliegue integral reproducible y explicar *"qué sucede cuando otro componente no responde"*.

### 1.2. El turista no salía del token (cualquiera podía hacerse pasar por otro)
- **Antes:** `turistaId` venía en el body JSON o en un query param, con `"turista1"` por defecto. Cualquier turista podía ver o cancelar reservas ajenas.
- **Ahora:**
  - El Gateway tiene un filtro (`UserHeadersFilter`) que **borra** los headers `X-User-*` que mande el cliente y los vuelve a armar desde el JWT: `X-User-Id` (sub), `X-User-Name` (preferred_username), `X-User-Email` y `X-User-Roles`.
  - `ms-reservas` toma el turista y su email de esos headers.
  - Un `TURISTA` solo puede ver y cancelar **sus** reservas (403 si no). Operador y admin pueden ver todas.
- **Por qué:** la pregunta de defensa *"¿cómo sabe reservas quién es el turista?"* tenía documentada una respuesta que no era real. Ahora sí lo es.

### 1.3. El clima casi nunca se validaba
- **Antes:** Open-Meteo devuelve por defecto solo **7 días** de pronóstico. Una reserva a 8 días caía en el fallback y nunca se rechazaba.
- **Ahora:** se pide `forecast_days=16`, el máximo que ofrece Open-Meteo.

### 1.4. `common-lib` estaba vacío
- **Antes:** el README decía que tenía el handler de errores, el DTO de error y `SecurityUtils`, pero **no tenía ni una clase**. Cada microservicio tenía su propia copia. El enunciado exige un JAR común **usado como dependencia** por los dos microservicios: no cumplíamos.
- **Ahora `common-lib` tiene:**
  - `GlobalExceptionHandler` (importado con `@Import(GlobalExceptionHandler.class)`): respuestas 400, 403, 404, 405, 409, 502 y 500 con el mismo formato.
  - `ErrorResponseDTO`: `{ timestamp, status, error, message, path, validaciones }`.
  - Excepciones: `BadRequestException`, `ResourceNotFoundException`, `ConflictException`, `ForbiddenException`.
  - `UserHeaders` y `UsuarioAutenticado`: el contrato de identidad entre el Gateway y los microservicios (es el "SecurityUtils").
- Se borraron las copias duplicadas de `ms-catalogo` y `ms-reservas`. **Queda fuera a propósito** todo lo que es dominio (entidades, repositorios, reglas): cada servicio conserva su modelo.

### 1.5. Keycloak perdía todo al recrear el contenedor
- **Antes:** el realm, los roles y los usuarios se creaban a mano y se perdían con `docker compose down`.
- **Ahora:** `keycloak/tpi-realm.json` se importa al arrancar: realm `tpi-realm`, cliente `tpi-client`, roles `TURISTA`/`OPERADOR`/`ADMIN` y 4 usuarios con email.
- **Por qué:** el enunciado pide un despliegue *"que no requiera configuraciones manuales"*.

---

## 🟢 2. Funcionalidades nuevas del dominio
Salen del enunciado (*"una reserva inicialmente válida podría dejar de ser viable"*, *"características de los participantes"*, *"época del año"*) y de nuestro documento (participantes con DNI, restricción de edad, regla de 48 hs, mails al confirmar y cancelar).

### 2.1. Participantes en la reserva
- Cada reserva lleva su lista de participantes: `nombre`, `dni` (7 u 8 dígitos), `fechaNacimiento` y `telefono` (opcional). Tabla `participantes` con FK `reserva_id`, como dice la sección 08 del documento.
- `cantidadPersonas` ya no se manda en el body: **es la cantidad de participantes**.
- No se aceptan DNI repetidos en una misma reserva (400).

### 2.2. Restricciones de edad y temporada
- Las actividades del catálogo tienen nuevos campos opcionales: `edadMinima`, `edadMaxima`, `mesInicioTemporada` y `mesFinTemporada`. La temporada puede cruzar el año: 9 a 4 = septiembre a abril.
- Al reservar se valida la edad que **va a tener cada participante el día de la excursión**, y que la fecha caiga dentro de la temporada (400 si no se cumple).
- Datos semilla para probarlo: Champaquí (14 a 65 años), San Roque (temporada sep-abr), Los Gigantes (+18, temporada mar-nov).

### 2.3. Revalidación: una reserva válida puede dejar de serlo ⭐
Es lo que más pesa del dominio que elegimos. El endpoint `POST /api/v1/reservas/revalidaciones` (ADMIN) revisa las reservas `PENDIENTE` y `CONFIRMADA` con fecha futura.

| Regla | Qué pasa |
|---|---|
| El prestador da de baja la actividad | → `CANCELADA` |
| Cambia la temporada y la fecha queda afuera | → `CANCELADA` |
| Bajan el cupo y las reservas no entran | Se cancelan las `PENDIENTE` más recientes; las `CONFIRMADA` se respetan |
| El pronóstico empeora (hasta 16 días) | → `RECHAZADA_POR_CLIMA` |
| Reserva `PENDIENTE` a 48 hs de la excursión | Mail de recordatorio para confirmar (se manda una sola vez) |
| Sigue `PENDIENTE` a 24 hs | → `CANCELADA` (se libera el cupo) |

- Si `ms-catalogo` no responde, **no se cancela nada** de esa actividad: se reintenta en la próxima ejecución. Una falla técnica no puede cancelar reservas de clientes.
- Cada cambio queda explicado en el nuevo campo **`motivoEstado`** y se avisa por mail.

### 2.4. Mails en cada evento (asincrónicos)
Se mandan al crear, al confirmar (funciona como **voucher**), al cancelar, al rechazar por clima y con el recordatorio de 48 hs. Son `@Async`: no demoran la respuesta, y si el SMTP falla la reserva no se ve afectada.

### 2.5. Filtro por ciudad en el catálogo
- Las actividades tienen un campo **`ciudad`** propio (ej. "Villa Carlos Paz") y `GET /actividades?ciudad=` filtra por ese campo.
- La búsqueda es parcial e insensible a mayúsculas (`toLowerCase().contains(...)`): `carlos paz`, `CARLOS PAZ` y `villa` encuentran "Villa Carlos Paz".
- **Si no hay coincidencias responde `200` con `[]`.** Es lo correcto en REST: la colección existe, solo que el filtro no encontró resultados. Un 404 sería para un recurso que no existe.

---

## 🏗️ 3. Arquitectura y despliegue

### 3.1. Trusted zone
- En Docker solo se publican el Gateway (8080), Keycloak (8180), la bandeja de Mailpit (8025) y Postgres solo en `127.0.0.1:5432`.
- Los microservicios, OSRM y el SMTP quedan **solo dentro de la red `tpi-network`**.
- **Por qué:** el enunciado pide justificar la trusted zone. Antes se podía llamar directo al 8082 y saltearse el Gateway, incluso mandando headers `X-User-*` falsos. Ahora la única puerta es el Gateway.

### 3.2. Dockerfiles simples (Apunte 21 y 22)
- Cada Dockerfile sigue el formato clásico de cátedra (`FROM eclipse-temurin:21-jre-alpine` + `COPY target/*.jar app.jar`). Primero se compila con Maven (`mvn clean package`) y después se levanta con `docker compose up --build -d`.

### 3.3. Otros ajustes
- `docker-compose.osrm.yml` queda **solo** para desarrollo local sin el compose completo (usarlos juntos choca, porque los dos crean un contenedor `osrm`).
- El gateway ahora espera a todos los servicios antes de arrancar.

---

## 🐛 4. Bugs corregidos

| Bug | Corrección |
|---|---|
| Datos semilla con `turista-1` y default `turista1` → "Mis reservas" no mostraba las semillas | Unificado a `turista1` |
| El filtro de identidad del Gateway daba **500** con requests reales (Netty entrega headers de solo lectura) | Se arma una copia de los headers. El test ahora reproduce el caso real |
| El handler de errores devolvía **500** para ruta inexistente, método no permitido o id no numérico | Ahora 404, 405 y 400 respectivamente |
| Cupo excedido y categoría con actividades devolvían 400 | Ahora **409 Conflict**, como dicen nuestros docs (RN01) |
| `@FutureOrPresent` dejaba reservar para hoy | `@Future`: la fecha debe ser posterior a hoy (sección 04 del documento) |

---

## 🧪 5. Tests

| Módulo | Tests |
|---|---|
| `ms-reservas` | `ReglasActividadTest` (edad, temporada, clima) y `RevalidacionServiceImplTest` (baja, catálogo caído, cupo, clima, 48 hs) |
| `api-gateway` | `UserHeadersFilterTest` (propaga identidad e ignora headers falsificados) |

```bash
mvn test
```

## 📬 6. Colección Postman nueva
`tpi_postman_collection.json`: **59 requests en 8 carpetas**, pensada para correr entera con el *Collection Runner*.

| Carpeta | Qué prueba |
|---|---|
| 00 · Autenticación | Tokens de los 4 usuarios (cada uno en su variable) |
| 01 · Catálogo público | Listados, filtros (incluida la ciudad), detalle, 404 |
| 02 · Gestión del catálogo | Crear categoría y actividad, PUT, PATCH, validaciones, 409 |
| 03 · Proceso principal | Crear → ver → confirmar → cancelar, y una reserva con chequeo de clima |
| 04 · Reglas de negocio | Cupo 409, edad, temporada, DNI repetido, validación, transiciones inválidas, 403 entre turistas |
| 05 · Revalidación | Reservar, dar de baja la actividad, revalidar y ver la reserva cancelada con su motivo |
| 06 · GeoAPI | Distancia, estimación, coordenada inválida, health de OSRM |
| 07 · Seguridad | 401, 403 por rol y el header `X-User-Name` falsificado que se ignora (**para la defensa**) |

- Cada request usa su propio token, trae tests automáticos y encadena IDs (`actividad_id`, `reserva_id`, …).
- Las fechas se calculan solas para el día en que se corre.
- **Es esperable:** sin `osrm-data/`, la carpeta 06 da 502 (el test lo acepta) y la reserva con clima puede salir PENDIENTE o RECHAZADA_POR_CLIMA según el pronóstico real.

---

## 📄 7. Documentación actualizada
- `README.md` (raíz): reescrito con la arquitectura real, los endpoints, el proceso, la trusted zone y cómo levantarlo.
- `ms-reservas/README.md`: reescrito (participantes, reglas, revalidación, transiciones de estado).
- `ms-catalogo/README.md`, `api-gateway/README.md`, `geoapi/README.md`, `ROLES-Y-PERMISOS-RBAC.md` y `GUIA-AUTENTICACION-KEYCLOAK-POSTMAN.md`: actualizados.
- `MS_RESERVAS.md`: quedó marcado como **histórico** (fue la guía de la primera versión).

---

## ✏️ 8. Qué hay que actualizar en nuestro Google Doc
El código ahora es la referencia. Para que el documento coincida:

- **RN02 (clima):** en un lugar dice "≥ 60 %" y en otro "> 70 %". El código usa **> 70 %** o `weather_code >= 61`.
- **RN03 (traslado):** si no hay ruta o OSRM está caído, ya no se devuelve 502/400: la reserva sigue con traslado "No disponible".
- **RN05 (estados):** agregar las transiciones `PENDIENTE`/`CONFIRMADA` → `RECHAZADA_POR_CLIMA` por revalidación.
- **Regla de 48 hs:** la implementamos como recordatorio a 48 hs y anulación a 24 hs si sigue pendiente. Si la idea era otra, se ajusta fácil.
- **Sección 07 (APIs):** `GET /api/v1/reservas` ya está implementado (sacar "pendiente de implementación") y falta agregar `POST /api/v1/reservas/revalidaciones` (ADMIN). En catálogo, el filtro `ciudad` ya existe.
- **Sección 09 (common-lib):** el campo de error es `message` (no `mensaje`) y la librería tiene más contenido (ver 1.4).
- **Sección 11 (arranque):** ahora es un solo comando: `docker compose up --build -d`.
- **Sección 12 (Postman):** la estructura cambió (ver sección 6 de este documento).
- **Diagrama C2:** conviene marcar que solo el Gateway queda expuesto (trusted zone) y que OSRM y geoapi están dentro del despliegue.

---

## 🎓 9. Argumentos para la defensa
- **¿Cómo sabe reservas quién es el turista?** El Gateway valida el JWT y reenvía la identidad en headers `X-User-*`, borrando antes los que mande el cliente. Como los microservicios no publican puertos, no hay forma de saltearse el Gateway.
- **¿Qué pasa si otro componente no responde?**
  - Open-Meteo o geoapi caídos: la reserva continúa en modo degradado.
  - SMTP caído: no afecta la reserva.
  - Catálogo caído: 502 al crear, y la revalidación no cancela nada por una falla técnica.
- **¿Qué compartieron en `common-lib` y qué no?** Lo transversal (errores, contrato de identidad, OpenAPI) sí. El dominio (entidades, reglas) no, para mantener independientes los servicios.
- **¿Por qué una búsqueda sin resultados da 200 y no 404?** Porque el recurso es la colección y existe: el filtro simplemente no encontró nada.
- **¿Cómo deja de ser viable una reserva?** Con la revalidación programada: baja, temporada, cupo, clima y la regla de 48 hs. Cada cambio queda con su `motivoEstado` y se notifica por mail.
