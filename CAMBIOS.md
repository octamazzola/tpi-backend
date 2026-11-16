# 📝 Cambios del proyecto: qué se hizo y por qué
**TPI Backend — Sistema de Gestión de Actividades Turísticas y Excursiones · Equipo 11**

Este documento es para que todo el equipo entienda, pruebe y pueda **defender** los cambios.
Cada cambio tiene el *antes*, el *ahora* y el *por qué*.

Los cambios salen de revisar el proyecto contra tres fuentes:
1. El **enunciado del TPI**, el **documento de alcance** y la **rúbrica** (105 puntos, 12 criterios).
2. **Nuestro cuestionario de compromiso** (el Google Doc con C1/C2, reglas RN01–RN05 y verificaciones).
3. Los **apuntes de la cátedra** (12 a 24).

### Dónde están los cambios
Rama **`alineacion-apuntes`**, con estos commits sobre lo que hoy está en `main` (el merge `6e63c7b`, que trae `098cf95`):

| Commit | Qué agrega |
|---|---|
| `f479791` | Correcciones críticas, reglas de dominio, revalidación, seguridad, common-lib, despliegue y Postman |
| `4fd5b26` | Alineación con los apuntes: Correlation ID, Actuator, healthchecks, Swagger documentado y 422 |
| `b494f9e` | `expose:` en el compose y header `Location` en los `201` |
| `ca883aa` | Este documento |
| `6c1e2ea` | URIs sin verbos: confirmar y cancelar pasan a `PATCH .../confirmacion` y `.../cancelacion` |
| (último) | Este documento completo, con la validación final |

### Estado verificado (16/11/2026, sobre el último commit)
| Chequeo | Resultado |
|---|---|
| Compilación de los 5 módulos (`mvn test`) | **BUILD SUCCESS, 23 tests en verde** (common-lib 2, ms-catalogo 4, ms-reservas 11, geoapi 1, api-gateway 5) |
| `docker compose config` | Válido. Solo se publican 8080, 8180, 8025 y `127.0.0.1:5432` |
| Docker | 8 contenedores recreados desde cero; los 4 servicios Spring, Postgres y Mailpit en *healthy* |
| Postman (colección completa, en orden) | **63 requests, 111 tests OK, 0 fallidos**, dos corridas seguidas (se puede repetir sin reiniciar nada) |
| Integraciones reales | OSRM calcula la ruta (112 km, 2 hs 2 min), Open-Meteo devuelve el pronóstico, Mailpit recibe los mails y el Correlation ID aparece en todos los servicios |

---

## ⚡ Qué tenés que hacer después de bajar la rama

1. **Borrar el volumen viejo de Postgres** (si no, no se crean `reservasdb` ni las tablas nuevas):
   ```bash
   docker compose down -v
   ```
2. **Levantar todo.** No hace falta compilar antes: los Dockerfiles compilan adentro.
   ```bash
   docker compose up --build -d
   ```
   El primer build tarda unos minutos. Con `docker compose ps` se ve cuando todo queda *healthy*.
3. **Reimportar Postman**: borrar la colección vieja, importar `tpi_postman_collection.json` y correrla entera con el *Collection Runner*. Ojo: la colección vieja usa `PUT .../confirmar` y `.../cancelar`, que ya no existen (sección 6).
4. **Keycloak ya no se configura a mano**: realm, cliente, roles y usuarios se importan solos.

| URL | Qué es |
|---|---|
| http://localhost:8080 | API Gateway (única entrada a las APIs) |
| http://localhost:8080/swagger-ui.html | Swagger con las 3 APIs documentadas |
| http://localhost:8080/actuator/health | Estado del gateway |
| http://localhost:8180 | Keycloak (admin / admin) |
| http://localhost:8025 | Bandeja de correos (Mailpit) |

Usuarios de prueba (clave `1234`): `turista1`, `turista2`, `operador1`, `admin1`.

> Para el mapa de OSRM hay que descargar `osrm-data/` del Drive (ver README). Sin el mapa todo funciona igual, pero las reservas quedan con traslado "No disponible".

---

## 🔴 1. Problemas críticos corregidos

| # | Antes | Ahora | Por qué |
|---|---|---|---|
| 1.1 | `geoapi` y `osrm` no estaban en el compose: en Docker **toda reserva daba 500** | Están en el compose, en la misma red. Si se caen, la reserva sigue con traslado "No disponible" | El enunciado pide un despliegue integral y explicar qué pasa si otro componente no responde |
| 1.2 | El turista salía del **body** o de `?turistaId=`: cualquiera podía reservar a nombre de otro y ver o cancelar reservas ajenas | El gateway pasa la identidad del token en headers `X-User-*` (borrando antes los que manda el cliente). Un turista solo ve y cancela **sus** reservas (403 si no) | RN04 de nuestro cuestionario y SEG-01. La frase *"el gateway le pasa el usuario al microservicio"* ahora es verdad |
| 1.3 | Open-Meteo devolvía **7 días**: a más de una semana el clima nunca se validaba | Se piden **16 días** (`forecast_days=16`) | RN02: la reserva de Postman a 8 días caía siempre en el fallback |
| 1.4 | `common-lib` estaba **vacío**; cada servicio tenía su copia de errores | Tiene el manejo de errores, las excepciones, el contrato de identidad y el filtro de Correlation ID (ver sección 4) | El enunciado exige un JAR común **usado como dependencia** por los microservicios: no cumplíamos |
| 1.5 | Keycloak se configuraba a mano y **perdía todo** al recrear el contenedor | `keycloak/tpi-realm.json` se importa al arrancar | El enunciado pide un despliegue *"que no requiera configuraciones manuales"* |

---

## 🟢 2. Dominio: reglas de negocio nuevas
Salen del enunciado (*"una reserva inicialmente válida podría dejar de ser viable"*, *"características de los participantes"*, *"época del año"*) y de nuestro cuestionario.

### 2.1. Participantes
- Cada reserva tiene su lista de personas: `nombre`, `dni` (7 u 8 dígitos), `fechaNacimiento` y `telefono` (opcional). Tabla `participantes` con FK `reserva_id`, como dice la sección 08 del cuestionario.
- `cantidadPersonas` ya no se manda: **es la cantidad de participantes**. No se aceptan DNI repetidos.

### 2.2. Edad y temporada
- Las actividades tienen `edadMinima`, `edadMaxima`, `mesInicioTemporada` y `mesFinTemporada`, todos opcionales. La temporada puede cruzar el año: 9 a 4 = septiembre a abril.
- Al reservar se valida la edad que **cada participante tendrá el día de la excursión** y que la fecha esté dentro de la temporada.
- Datos semilla: Champaquí (14 a 65 años), San Roque (septiembre a abril), Los Gigantes (+18, marzo a noviembre).

### 2.3. Revalidación: una reserva válida puede dejar de serlo ⭐
Es lo que más pesa del dominio que elegimos. Corre **cada hora** (`@Scheduled`) y el admin también la puede disparar con `POST /api/v1/reservas/revalidaciones`. Revisa las reservas `PENDIENTE` y `CONFIRMADA` con fecha futura:

| Regla | Resultado |
|---|---|
| El prestador da de baja la actividad | → `CANCELADA` |
| Cambia la temporada y la fecha queda afuera | → `CANCELADA` |
| Bajan el cupo y las reservas no entran | Se cancelan las `PENDIENTE` más recientes; las `CONFIRMADA` se respetan |
| El pronóstico empeora (hasta 16 días) | → `RECHAZADA_POR_CLIMA` |
| Reserva `PENDIENTE` a 48 hs de la excursión | Mail pidiendo confirmar (una sola vez) |
| Sigue `PENDIENTE` a 24 hs | → `CANCELADA` (libera el cupo) |

- Si `ms-catalogo` no responde, **no se cancela nada** de esa actividad: una falla técnica no puede cancelar reservas de clientes. Se reintenta en la próxima ejecución.
- Cada cambio queda explicado en el campo nuevo **`motivoEstado`** y se avisa por mail.
- **Sobre el `@Scheduled`:** no aparece en los apuntes, pero lo dejamos a propósito. La regla de 48 hs es la verificación 5 de nuestro cuestionario y depende del paso del tiempo: sin algo que la dispare sola, solo se cumpliría si alguien llama al endpoint a mano.

### 2.4. Mails en cada evento
Al crear, al confirmar (funciona como **voucher**), al cancelar, al rechazar por clima y con el recordatorio de 48 hs. Son asincrónicos (`@Async`): no demoran la respuesta, y si el SMTP falla la reserva no se ve afectada.

### 2.5. Filtro por ciudad en el catálogo
- Las actividades tienen un campo **`ciudad`** propio y `GET /actividades?ciudad=` filtra por ese campo.
- Es una búsqueda parcial que no distingue mayúsculas ni tildes: `carlos paz` encuentra "Villa Carlos Paz".
- **Si no hay coincidencias responde `200` con `[]`**: la colección existe, solo que el filtro no encontró resultados. Antes buscaba en toda la dirección y "Córdoba" (la provincia) traía todas las actividades.

### 2.6. Estados de una reserva (RN05)
| Desde | Hacia | Quién |
|---|---|---|
| (nueva) | `PENDIENTE` / `RECHAZADA_POR_CLIMA` | Turista al crear |
| `PENDIENTE` | `CONFIRMADA` | Operador o admin |
| `PENDIENTE`, `CONFIRMADA` | `CANCELADA` | Turista titular, admin o la revalidación |
| `PENDIENTE`, `CONFIRMADA` | `RECHAZADA_POR_CLIMA` | Revalidación |
| `CANCELADA`, `RECHAZADA_POR_CLIMA` | — | Estados finales: cualquier intento → `400` |

---

## 🔐 3. Seguridad y trusted zone

| Cambio | Por qué |
|---|---|
| **`UserHeadersFilter`** en el gateway: borra los `X-User-*` que manda el cliente y los arma desde el JWT (`sub`, `preferred_username`, `email`, roles) | Los microservicios saben quién hace la operación sin confiar en el cuerpo del pedido |
| **Control de titular** en `ms-reservas`: un turista solo ve y cancela sus reservas | RN04 de nuestro cuestionario |
| **Trusted zone**: solo se publican el gateway (8080), Keycloak (8180), la web de Mailpit (8025) y Postgres en `127.0.0.1` | Antes se podía llamar directo al 8082, saltearse el gateway y mandar headers falsos |
| **`expose:`** en los servicios internos (Apunte 22) | Documenta el puerto de cada servicio dentro de la red sin publicarlo en el host |
| `POST /reservas/revalidaciones` exclusivo de `ADMIN` en el gateway | Es una operación administrativa |

---

## 📦 4. Componente común (`common-lib`)
JAR Maven usado por `ms-catalogo`, `ms-reservas` y `api-gateway`. Se registra solo al arrancar, por auto-configuración de Spring Boot.

| Contenido | Para qué |
|---|---|
| `GlobalExceptionHandler` + `ErrorResponseDTO` | Mismo formato de error en todos: `{ timestamp, status, error, message, path, validaciones }` |
| `BadRequestException` (400), `ForbiddenException` (403), `ResourceNotFoundException` (404), `ConflictException` (409), `UnprocessableEntityException` (422) | Excepciones de negocio compartidas |
| `UserHeaders` / `UsuarioAutenticado` | Contrato de identidad entre el gateway y los servicios (el "SecurityUtils" del cuestionario) |
| `CorrelationIdFilter` | Pone el Correlation ID en el log de cada petición (ver sección 5) |
| Configuración OpenAPI común | Esquema Bearer JWT: botón *Authorize* en Swagger |

**Queda afuera a propósito** todo lo que es dominio (entidades, repositorios, reglas). Si se compartiera, un cambio en catálogo obligaría a redesplegar reservas.

---

## 📈 5. Trazabilidad y monitoreo (Apuntes 22, 23 y 24)

| Cambio | Por qué |
|---|---|
| **Correlation ID** (`X-Correlation-ID`): el gateway lo genera, cada servicio lo pone en el log (`INFO [id] ...`), `RestClient` lo propaga a catálogo y geoapi, y llega también al hilo del mail asincrónico | La rúbrica (INT-01) pide *"trazabilidad verificable"*: con un solo identificador se sigue una reserva por todos los servicios |
| **Actuator** (`health`, `info`, `metrics`) en todos los servicios | Monitoreo del estado de cada servicio (Apunte 24) |
| **Healthchecks** en el compose con `condition: service_healthy` | El Apunte 22 advierte que `depends_on` solo no garantiza que el servicio esté listo. Ahora reservas espera a catálogo y geoapi, y el gateway espera a los tres |

**Para mostrarlo en la defensa:** en Postman, carpeta 08, ejecutar *"El Correlation ID enviado se respeta de punta a punta"* (manda `X-Correlation-ID: demo-defensa`) y después:
```powershell
docker compose logs | findstr demo-defensa
```
Aparece la misma operación en el gateway, ms-reservas, ms-catalogo, geoapi y el envío del mail.

---

## 🌐 6. API y documentación

### Códigos de respuesta (Apunte 14)
| Código | Cuándo |
|---|---|
| `200` / `201` / `204` | Éxito. Los `201` traen el header **`Location`** con la URI del recurso creado |
| `400` | Datos mal formados o incompletos, y transiciones de estado no permitidas (RN05) |
| `401` / `403` | Sin token o token inválido / rol insuficiente o reserva ajena |
| `404` | El recurso no existe (o la actividad fue dada de baja) |
| `409` | Conflicto con el estado actual: cupo insuficiente, categoría con actividades |
| `422` | Datos válidos que **no cumplen una regla de negocio**: edad, temporada, DNI repetido, edad mínima mayor que la máxima |
| `502` | Un servicio colaborador no responde (ej. catálogo) |

Una búsqueda sin resultados da `200` con `[]`, no `404`.

### Swagger
- Agrupado en el gateway: http://localhost:8080/swagger-ui.html, con un desplegable para Catálogo, Reservas y GeoAPI. Es necesario porque los microservicios no publican puertos.
- Los controllers tienen `@Tag`, `@Operation` y `@ApiResponses` (Apunte 17): cada endpoint muestra qué hace y qué códigos devuelve.

### Endpoints nuevos
- `POST /api/v1/reservas/revalidaciones` (ADMIN).
- `GET /api/v1/reservas` (ADMIN), que el cuestionario daba como pendiente.
- Filtro `ciudad` en `GET /api/v1/catalogo/actividades`.

### URIs sin verbos
Confirmar y cancelar tenían el verbo en la URI (`PUT /reservas/{id}/confirmar` y `/cancelar`). REST pide que la URI nombre un recurso con un **sustantivo** y que la acción la diga el método HTTP, así que ahora siguen el formato del ejemplo de la cátedra en la consigna de la tabla de endpoints:

| Antes | Ahora | Roles |
|---|---|---|
| `PUT /api/v1/reservas/{id}/confirmar` | `PATCH /api/v1/reservas/{id}/confirmacion` | OPERADOR, ADMIN |
| `PUT /api/v1/reservas/{id}/cancelar` | `PATCH /api/v1/reservas/{id}/cancelacion` | TURISTA (solo las propias), ADMIN |

- **Por qué sustantivo y no infinitivo:** "confirmar" y "cancelar" ya *son* infinitivos, es decir, verbos. La regla pide nombrar la cosa (la confirmación, la cancelación) y dejar la acción al método.
- **Por qué PATCH y no PUT:** PUT reemplaza el recurso entero; PATCH modifica una parte. Confirmar o cancelar solo cambia el estado de la reserva.
- **Por qué sub-recursos y no `PATCH /reservas/{id}` con `{"estado": ...}` en el body:** con la ruta separada, el gateway sigue controlando los roles por URL (confirmar: OPERADOR/ADMIN; cancelar: TURISTA/ADMIN). Con el estado en el body, el gateway no podría distinguirlos y ese control tendría que pasar al microservicio.
- No llevan body y la lógica del service es la misma de antes: solo cambiaron la anotación del controller, las dos reglas del `SecurityConfig`, Postman y los READMEs.

---

## 🐳 7. Despliegue

| Cambio | Por qué |
|---|---|
| **Dockerfiles multi-stage**: compilan cada módulo adentro del contenedor | Alcanza con `docker compose up --build`, sin Maven en la máquina. INF-01 pide que lo pueda levantar un tercero |
| `.dockerignore` excluye `osrm-data` (+4 GB) y `target/` | Que el build no mande gigas a Docker |
| Realm de Keycloak importado (`keycloak/tpi-realm.json`) | Sin configuración manual |
| `docker-compose.osrm.yml` queda solo para desarrollo sin el compose completo | Usarlos juntos choca: los dos crean un contenedor `osrm` |

---

## 🐛 8. Bugs corregidos

| Bug | Corrección |
|---|---|
| Datos semilla con `turista-1` y default `turista1`: "Mis reservas" no mostraba las semillas | Unificado a `turista1` |
| Estado inicial de las reservas | `PENDIENTE` |
| Cupo excedido y categoría con actividades respondían 400 | `409 Conflict` (RN01 del cuestionario) |
| Se podía reservar para el mismo día | `@Future`: la fecha debe ser posterior a hoy |
| El filtro de identidad del gateway daba 500 con requests reales (Netty entrega headers de solo lectura) | Se arma una copia de los headers. El test reproduce el caso real |
| El handler de errores devolvía 500 en una ruta inexistente, un método no permitido o un id no numérico | Ahora 404, 405 y 400 |
| El header `X-Correlation-ID` salía duplicado en la respuesta | El gateway lo fija una sola vez antes de responder |
| `/actuator/info` salía vacío | Habilitado `management.info.env.enabled` |

---

## 🧪 9. Tests y Postman

### Tests unitarios (23)
| Módulo | Qué prueban |
|---|---|
| `common-lib` | Filtro de Correlation ID |
| `ms-reservas` | Reglas (edad, temporada, clima) y revalidación (baja, catálogo caído, cupo, clima, 48 hs) con reloj fijo |
| `ms-catalogo` | Búsqueda sin tildes ni mayúsculas |
| `api-gateway` | Propagación de identidad, headers falsificados y Correlation ID |

### Colección Postman: 63 requests en 9 carpetas
| Carpeta | Qué prueba |
|---|---|
| 00 · Autenticación | Tokens de los 4 usuarios, cada uno en su variable |
| 01 · Catálogo público | Listados, filtros (incluida la ciudad), lista vacía, 404 |
| 02 · Gestión del catálogo | Crear categoría y actividad (con `Location`), PUT, PATCH, 400, 422, 409 |
| 03 · Proceso principal | Crear → ver → confirmar → cancelar, y reserva con chequeo de clima |
| 04 · Reglas de negocio | Cupo 409, edad/temporada/DNI 422, validación 400, transiciones 400, 403 entre turistas |
| 05 · Revalidación | Reservar, dar de baja la actividad, revalidar y ver la reserva cancelada con su motivo |
| 06 · GeoAPI | Distancia, estimación, coordenada inválida, health de OSRM |
| 07 · Seguridad | 401, 403 por rol y header `X-User-Name` falsificado (para la defensa) |
| 08 · Trazabilidad y monitoreo | Correlation ID de punta a punta y health del gateway |

Las fechas se calculan solas y los IDs se encadenan entre carpetas.

---

## ⚠️ 10. Relación con el commit de Octavio (`f53a5f9`)
En GitHub, `ms-reservas-impl` tiene un commit de Octavio que simplifica cosas para alinearse con los apuntes. **Esta rama no lo incluye**, porque algunas de esas simplificaciones chocan con lo de arriba. Hay que decidir juntos qué se queda:

| Lo que saca `f53a5f9` | Qué implica | Propuesta |
|---|---|---|
| `RevalidacionScheduler` (`@Scheduled`) | La regla de 48 hs ya no corre sola | **Mantener**: es un compromiso del cuestionario (verificación 5) |
| Swagger agrupado del gateway | Con los puertos cerrados, **Swagger no se puede abrir** desde afuera, y el enunciado lo exige | **Mantener** |
| Auto-configuración de common-lib | Esta rama la usa también para registrar el filtro del Correlation ID; con `@Import` manual hay que importar los dos componentes | Cualquiera de las dos sirve si se registran ambos |
| Dockerfiles multi-stage | Vuelve el paso `mvn clean install` antes del compose | Cualquiera de las dos; multi-stage es más reproducible |
| `ClockConfig` | Los tests de 48 hs pasan a depender de la fecha real | Cualquiera de las dos |
| `TextoNormalizado` | El filtro por ciudad vuelve a distinguir tildes | Cualquiera de las dos |

---

## ✏️ 11. Qué hay que actualizar en nuestro Google Doc
El código ahora es la referencia, y el documento de alcance pide **comunicar los cambios al docente**:

- **RN02:** en un lugar dice "≥ 60 %" y en otro "> 70 %". El código usa **> 70 %** o `weather_code ≥ 61`.
- **RN03:** si OSRM no responde ya no se devuelve 502/400: la reserva sigue con traslado "No disponible".
- **RN05:** agregar las transiciones por revalidación (sección 2.6).
- **Códigos:** edad, temporada y DNI repetido responden **422** (antes no estaban contemplados).
- **Regla de 48 hs:** implementada como recordatorio a 48 hs y anulación a 24 hs.
- **Consulta a catálogo:** reservas usa `GET /actividades/{id}` (necesita precio, coordenadas y restricciones); la ocupación por fecha la calcula reservas.
- **DER:** participante con `fecha_nacimiento` y `telefono`; reserva con `motivo_estado` y `recordatorio_enviado`; actividad con `ciudad`, edades y temporada.
- **Sección 07 (APIs):** `GET /reservas` ya implementado; agregar `POST /reservas/revalidaciones`; filtro `ciudad`.
- **Sección 07 y RN04:** confirmar y cancelar pasan a `PATCH /reservas/{id}/confirmacion` y `PATCH /reservas/{id}/cancelacion` (sección 6).
- **Sección 09 (common-lib):** campo `message` (no `mensaje`) y el contenido de la sección 4.
- **Sección 11 (arranque):** un solo comando.
- **Sección 12 (Postman):** 9 carpetas (sección 9 de este documento).
- **Diagrama C2:** marcar que solo el gateway está expuesto, y que OSRM y geoapi forman parte del despliegue.

---

## 🎓 12. Argumentos para la defensa
- **¿Cómo sabe reservas quién es el turista?** El gateway valida el JWT y reenvía la identidad en headers `X-User-*`, borrando antes los que manda el cliente. Los microservicios no publican puertos (`expose:` en lugar de `ports:`), así que no hay forma de saltearse el gateway.
- **¿Qué pasa si un componente no responde?** Catálogo: 502 al crear, y la revalidación no cancela nada. Open-Meteo y geoapi: la reserva sigue en modo degradado. SMTP: no afecta la reserva.
- **¿Cómo siguen una operación entre servicios?** Con el Correlation ID: un solo identificador aparece en los logs de todos los servicios.
- **¿Por qué 422 y no 400?** 400 es un pedido mal formado; 422, un pedido válido que no cumple una regla del negocio (Apunte 14).
- **¿Qué compartieron en common-lib y qué no?** Lo transversal sí (errores, identidad, logging, OpenAPI). El dominio no, para mantener los servicios independientes.
- **¿Cómo deja de ser viable una reserva?** Con la revalidación: baja, temporada, cupo, clima y la regla de 48 hs, cada cambio con su `motivoEstado` y su mail.
- **¿Por qué una búsqueda vacía da 200?** Porque el recurso es la colección y existe: el filtro no encontró resultados.
- **¿Por qué `PATCH /reservas/{id}/confirmacion` y no `PUT /confirmar`?** La URI nombra recursos con sustantivos y la acción la dice el método. Es PATCH porque solo cambia el estado, y es el formato del ejemplo que da la cátedra en la consigna de la tabla de endpoints.
