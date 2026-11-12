# 🎫 Microservicio de Reservas (`ms-reservas`)
**Backend de Aplicaciones — UTN FRC (2026)**  
**Dominio:** Sistema de Gestión de Actividades Turísticas y Excursiones  
**Tecnología:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Mail, RestClient, `@Scheduled`, Docker

---

## 📋 1. Descripción y Responsabilidad

`ms-reservas` es el **orquestador central del negocio**. Gestiona el ciclo de vida completo de las reservas y decide, en el momento de reservar **y después**, si una reserva sigue siendo viable.

### Responsabilidades clave
* **Orquestación del proceso principal:** valida la actividad y sus restricciones en `ms-catalogo`, consulta el pronóstico en **Open-Meteo**, calcula el traslado con `geoapi` (OSRM) y notifica por correo (**Mailpit**).
* **Participantes:** cada reserva registra a las personas que asisten (nombre, DNI, fecha de nacimiento y teléfono). La cantidad de personas es la cantidad de participantes.
* **Restricciones de participación:** edad mínima/máxima de cada participante el día de la excursión y temporada de la actividad.
* **Revalidación de reservas:** un proceso programado (cada hora) vuelve a evaluar las reservas vigentes: baja de la actividad, cambio de temporada, reducción de cupo, pronóstico adverso y la **regla de 48 hs**.
* **Estados y transiciones (RN05):** `PENDIENTE`, `CONFIRMADA`, `CANCELADA`, `RECHAZADA_POR_CLIMA`, con el campo `motivoEstado` que explica cada cambio.
* **Resiliencia:** si Open-Meteo, geoapi o el SMTP fallan, la reserva continúa en modo degradado. Si `ms-catalogo` no responde: al crear se devuelve `502`; al revalidar, esa actividad no se toca.
* **Separated Interface (SOLID - DIP):** contratos (`ReservaService`, `RevalidacionService`, `EmailService`) separados de sus implementaciones.

---

## 🔄 2. Proceso principal: `POST /api/v1/reservas`

```
[Turista] ──► [api-gateway] valida JWT + rol, propaga X-User-* ──► [ms-reservas]
                                                                     │
   1. GET actividad ─────────────────────────────────────────────────┼──► ms-catalogo
   2. temporada + edad de cada participante (reglas locales)         │
   3. cupo de la fecha (reservas PENDIENTE + CONFIRMADA)             │
   4. pronóstico (si requiereBuenClima) ─────────────────────────────┼──► Open-Meteo
   5. traslado origen → punto de encuentro ──────────────────────────┼──► geoapi ──► OSRM
   6. monto = precioBase × participantes                             │
   7. persistir (PENDIENTE o RECHAZADA_POR_CLIMA)                    │
   8. email asincrónico ─────────────────────────────────────────────┴──► Mailpit
```

| Paso | Regla | Resultado si falla |
|---|---|---|
| 0. Identidad | El turista (`X-User-Name`) y su email (`X-User-Email`) salen del JWT, no del cuerpo. | — |
| 1. Catálogo | La actividad debe existir y estar activa. | `404` (o `502` si catálogo no responde) |
| 2. Temporada | La fecha debe caer entre `mesInicioTemporada` y `mesFinTemporada` (admite temporadas que cruzan el año). | `400` |
| 2. Edad | Cada participante debe cumplir `edadMinima` / `edadMaxima` **el día de la excursión**. | `400` |
| 2. DNI | No puede haber DNI repetidos en la misma reserva. | `400` |
| 3. RN01 Cupo | `ocupados + participantes <= cupoMaximo` para esa fecha. | `409 Conflict` |
| 4. RN02 Clima | Lluvia `> 70 %` o `weather_code >= 61` → `RECHAZADA_POR_CLIMA` (no ocupa cupo). | Open-Meteo caído o fecha a +16 días: sigue sin alerta |
| 5. RN03 Traslado | Distancia y duración desde el origen del turista. | geoapi/OSRM caído: `"No disponible"` |

Respuesta: `201 Created` con la reserva enriquecida y email “Recibimos tu solicitud”.

---

## ♻️ 3. Revalidación: una reserva válida puede dejar de serlo

Corre sola cada hora (`REVALIDACION_CRON`, por defecto `0 0 * * * *`) y el ADMIN puede dispararla con `POST /api/v1/reservas/revalidaciones`. Revisa las reservas `PENDIENTE` y `CONFIRMADA` con fecha futura, en este orden:

| Regla | Qué detecta | Cambio |
|---|---|---|
| Baja de la actividad | Catálogo devuelve 404 / `activa = false` | → `CANCELADA` |
| Cambio de temporada | El prestador cambió la temporada y la fecha quedó afuera | → `CANCELADA` |
| Reducción de cupo | Las reservas superan el nuevo `cupoMaximo` | Se cancelan las `PENDIENTE` **más recientes**; las `CONFIRMADA` se respetan |
| Clima | Pronóstico adverso (hasta 16 días) en actividades que requieren buen clima | → `RECHAZADA_POR_CLIMA` |
| Regla de 48 hs | Reserva `PENDIENTE` a 48 hs de la excursión | Email de recordatorio (una sola vez) |
| Regla de 48 hs | Sigue `PENDIENTE` a 24 hs de la excursión | → `CANCELADA` (libera el cupo) |

Cada cambio se notifica por email y queda explicado en `motivoEstado`. Si `ms-catalogo` no responde, esas reservas **no se tocan** (no se cancela nada por una falla técnica) y se reintenta en la próxima ejecución. La respuesta del endpoint resume la ejecución:

```json
{
  "ejecutadaEn": "2026-11-12T17:00:00",
  "reservasRevisadas": 4,
  "actividadesNoVerificadas": 0,
  "recordatoriosEnviados": 1,
  "cambios": [
    { "reservaId": 6, "actividadId": 5, "fechaExcursion": "2026-11-18",
      "estadoAnterior": "PENDIENTE", "estadoNuevo": "CANCELADA",
      "motivo": "La actividad fue dada de baja del catálogo por el prestador" }
  ]
}
```

### Transiciones de estado (RN05)

| Desde | Hacia | Quién |
|---|---|---|
| (nueva) | `PENDIENTE` / `RECHAZADA_POR_CLIMA` | Turista al crear |
| `PENDIENTE` | `CONFIRMADA` | Operador / Admin (email con voucher) |
| `PENDIENTE`, `CONFIRMADA` | `CANCELADA` | Turista titular, Admin o la revalidación |
| `PENDIENTE`, `CONFIRMADA` | `RECHAZADA_POR_CLIMA` | Revalidación (pronóstico actualizado) |
| `CANCELADA`, `RECHAZADA_POR_CLIMA` | — | Estados finales: cualquier transición → `400` |

---

## 🗺️ 4. Endpoints y seguridad

Todo entra por el `api-gateway` (`http://localhost:8080`), que valida el JWT de Keycloak y los roles. En Docker el puerto `8082` **no se publica** (trusted zone).

| Método | Endpoint | Rol | Descripción |
|:---:|:---|:---:|:---|
| `POST` | `/api/v1/reservas` | `TURISTA`, `ADMIN` | Crea una reserva ejecutando toda la orquestación. `201 Created`. |
| `GET` | `/api/v1/reservas` | `ADMIN` | Todas las reservas del sistema (auditoría). |
| `GET` | `/api/v1/reservas/mis-reservas` | `TURISTA`, `ADMIN` | Reservas del usuario del token. |
| `GET` | `/api/v1/reservas/{id}` | Autenticado | Detalle. Un `TURISTA` solo ve las propias (`403`). |
| `GET` | `/api/v1/reservas/actividad/{actividadId}` | `OPERADOR`, `ADMIN` | Contingente de una excursión. |
| `PUT` | `/api/v1/reservas/{id}/confirmar` | `OPERADOR`, `ADMIN` | `PENDIENTE` → `CONFIRMADA` + voucher por email. |
| `PUT` | `/api/v1/reservas/{id}/cancelar` | `TURISTA`, `ADMIN` | → `CANCELADA` + email. Un `TURISTA` solo cancela las propias. |
| `POST` | `/api/v1/reservas/revalidaciones` | `ADMIN` | Ejecuta la revalidación en el momento. |

---

## 🌐 5. Contratos JSON

### Crear reserva — request
```json
{
  "actividadId": 3,
  "fechaExcursion": "2026-11-18",
  "participantes": [
    { "nombre": "Lucía Fernández", "dni": "38123456", "fechaNacimiento": "1994-05-12", "telefono": "+54 351 555-1234" },
    { "nombre": "Martín Fernández", "dni": "37987654", "fechaNacimiento": "1992-09-03" }
  ],
  "origenTurista": { "latitud": -31.4135, "longitud": -64.18105, "direccion": "Hotel Sheraton, Córdoba Capital" }
}
```
* `fechaExcursion` debe ser futura; `participantes` entre 1 y 50; DNI de 7 u 8 dígitos.
* `turistaId` y `emailTurista` **no se envían**: salen del token. Solo se aceptan en pruebas directas al `8082` sin gateway.

### Crear reserva — respuesta `201 Created`
```json
{
  "id": 5,
  "turistaId": "turista1",
  "emailTurista": "turista1@example.com",
  "actividadId": 3,
  "tituloActividad": "Cabalgata al Peñón del Águila y Bosques Suizos",
  "fechaExcursion": "2026-11-18",
  "cantidadPersonas": 2,
  "participantes": [ { "nombre": "Lucía Fernández", "dni": "38123456", "fechaNacimiento": "1994-05-12", "telefono": "+54 351 555-1234" }, { "...": "..." } ],
  "montoTotal": 64000.0,
  "estado": "PENDIENTE",
  "motivoEstado": "Pendiente de confirmación por el prestador",
  "trasladoEstimado": { "distanciaKm": 92.4, "duracionFormateada": "1 hs 50 min" },
  "alertaClimatica": false,
  "descripcionClima": null,
  "fechaCreacion": "2026-11-12T16:00:00"
}
```

### Errores
Formato común de `common-lib`: `{ "timestamp", "status", "error", "message", "path", "validaciones" }`.

---

## ⚙️ 6. Configuración

[`application.properties`](src/main/resources/application.properties) usa H2 en memoria por defecto; en Docker las variables de entorno lo pasan a PostgreSQL (`reservasdb`).

| Variable | Default | Uso |
|---|---|---|
| `MS_CATALOGO_URL` | `http://localhost:8081/api/v1/catalogo` | ms-catalogo |
| `GEOAPI_URL` | `http://localhost:8083/api/v1/geo` | geoapi |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | `localhost` / `1025` | Mailpit |
| `REVALIDACION_HABILITADA` | `true` | Apaga el proceso programado |
| `REVALIDACION_CRON` | `0 0 * * * *` | Frecuencia de la revalidación |

---

## 🏗️ 7. Estructura del código

```text
ms-reservas/src/main/java/ms_reservas/
├── MsReservasApplication.java      # @EnableAsync (emails) + @EnableScheduling (revalidación)
├── config/
│   ├── RestClientConfig.java       # RestClient con timeouts cortos (3 s conexión / 5 s lectura)
│   ├── ClockConfig.java            # Reloj inyectable (zona Córdoba) para testear reglas de fechas
│   ├── RevalidacionScheduler.java  # @Scheduled que dispara la revalidación
│   └── DataLoader.java             # 4 reservas semilla (incluye una a 48 hs para la demo)
├── controllers/ReservaController.java
├── entities/                       # Reserva (1) ── (1..N) Participante, EstadoReserva
├── repositories/ReservaRepository.java
├── mappers/ReservaMapper.java
├── dtos/
│   ├── request/                    # ReservaRequestDTO, ParticipanteDTO, OrigenTuristaDTO
│   ├── response/                   # ReservaResponseDTO, TrasladoEstimadoDTO, ResultadoRevalidacionDTO
│   └── external/                   # Contratos de ms-catalogo, geoapi y Open-Meteo
├── clients/                        # CatalogoClient, GeoApiClient (fallback), OpenMeteoClient (fallback)
└── services/
    ├── ReglasActividad.java        # Reglas puras: edad, temporada, clima adverso
    ├── ReservaService.java / RevalidacionService.java / EmailService.java
    └── impl/                       # ReservaServiceImpl, RevalidacionServiceImpl, EmailServiceImpl (@Async)
```
Las excepciones, el `ErrorResponseDTO`, el `GlobalExceptionHandler` y la lectura del usuario autenticado vienen de **`common-lib`**.

### Tests
* `ReglasActividadTest`: edad al día de la excursión, temporadas que cruzan el año, umbral de clima.
* `RevalidacionServiceImplTest`: baja de actividad, catálogo caído, reducción de cupo, clima y regla de 48 hs (con reloj fijo).

```bash
mvn test -pl ms-reservas -am
```

---

## 🐳 8. Ejecución

* **Con Docker (recomendado):** `docker compose up --build -d` desde la raíz. Swagger: `http://localhost:8080/swagger-ui.html` → *Reservas (ms-reservas)*. Correos: `http://localhost:8025`.
* **Local sin Docker:** `mvn -pl ms-reservas spring-boot:run` → `http://localhost:8082/swagger-ui.html`, consola H2 en `/h2-console` (`jdbc:h2:mem:reservasdb`, usuario `sa`).

## 🧪 9. Postman

La colección [`tpi_postman_collection.json`](../tpi_postman_collection.json) cubre este servicio en las carpetas **03** (camino feliz), **04** (reglas de negocio negativas) y **05** (revalidación).
