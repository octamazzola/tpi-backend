# 🎫 Microservicio de Reservas (`ms-reservas`)
**Backend de Aplicaciones — UTN FRC (2026)**  
**Dominio:** Sistema de Gestión de Actividades Turísticas y Excursiones  
**Tecnología:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Mail, RestClient, Docker

---

## 📋 1. Descripción y Responsabilidad

El microservicio `ms-reservas` es el **orquestador central de negocio** del sistema. Es el encargado de gestionar el ciclo de vida completo de las reservas de excursiones, coordinando la interacción sincrónica y asincrónica entre los servicios del ecosistema y ejecutando las reglas clave de negocio:

### Responsabilidades Clave:
* **Orquestación del Proceso Principal:** Al recibir una solicitud de reserva, valida la existencia y disponibilidad de cupos en `ms-catalogo`, consulta el pronóstico meteorológico en **Open-Meteo**, calcula el tiempo de viaje y la distancia terrestre mediante `geoapi` (OSRM), y despacha notificaciones por correo electrónico a través de **Mailpit**.
* **Gestión de Estados del Negocio:** Administra el ciclo de vida de la reserva (`PENDIENTE`, `CONFIRMADA`, `CANCELADA`, `RECHAZADA_POR_CLIMA`), garantizando que toda reserva nueva inicie en `PENDIENTE` para su posterior confirmación por el operador turístico tras verificar el pago o 48 hs antes de la salida.
* **Resiliencia y Tolerancia a Fallos (Fallback):** Si la API externa de Open-Meteo sufre un timeout o caída de conectividad, el servicio aplica un mecanismo de degradación controlada continuando la reserva sin alerta climática en lugar de fallar. Asimismo, fallos temporales en el servidor SMTP no revierten la transacción de la reserva.
* **Separated Interface (SOLID - DIP):** Implementa desacoplamiento estricto separando contratos abstractos (`ReservaService`, `EmailService`) de sus implementaciones concretas.

---

## 🔄 2. Flujo de Orquestación Inter-Servicio

```
                      [Turista / Cliente]
                               │
                               ▼ POST /api/v1/reservas
                     ┌───────────────────┐
                     │    ms-reservas    │ (Servicio Orquestador - 8082)
                     └─┬───────┬───────┬─┘
                       │       │       │
       1. Consulta     │       │       │ 3. Valida clima
          actividad    │       │       │    (si requiereBuenClima)
          y cupos      ▼       │       ▼
               ┌─────────────┐ │   ┌───────────────────────┐
               │ ms-catalogo │ │   │ Open-Meteo (Internet) │
               │ (Port 8081) │ │   │ API Pública Externa   │
               └─────────────┘ │   └───────────────────────┘
                               │
                               │ 2. Calcula ruta y traslado
                               ▼    (Origen turista -> Destino actividad)
                       ┌──────────────┐
                       │    geoapi    │
                       │ (OSRM Local) │
                       └──────────────┘
```

### Paso a paso de la orquestación (`POST /api/v1/reservas`):
1. **Validación en Catálogo:** Consulta `GET /api/v1/catalogo/actividades/{id}`. Si la actividad no existe o está inactiva (`activa == false`), aborta con `404 Not Found`.
2. **Control Estricto de Cupo:** Suma las personas ya reservadas para esa fecha en estados `PENDIENTE` y `CONFIRMADA`. Si `yaReservadas + cantidadPersonas > cupoMaximo`, aborta con `400 Bad Request`.
3. **Chequeo Meteorológico:** Si `requiereBuenClima == true`, consulta Open-Meteo. Si la probabilidad de lluvia es $\ge 70\%$ o hay tormenta (`weatherCode >= 61`), la reserva se crea en estado `RECHAZADA_POR_CLIMA`. Si Open-Meteo no responde, activa fallback y continúa.
4. **Cálculo Vial OSRM:** Invoca `POST /api/v1/geo/rutas/distancia` enviando origen y destino anidados, obteniendo `distanciaKm` y `duracionFormateada` (ej. `"2 hs 15 min"`).
5. **Cálculo de Monto:** `montoTotal = precioBase * cantidadPersonas`.
6. **Persistencia:** Almacena la entidad con estado inicial **`PENDIENTE`** (o `RECHAZADA_POR_CLIMA`).
7. **Notificación SMTP:** Envía de forma asincrónica un correo al turista con el resumen y tiempo de traslado mediante Mailpit.
8. **Respuesta al Cliente:** Retorna HTTP `201 Created` con el DTO enriquecido.

---

## 🗺️ 3. Mapa de Endpoints y Seguridad RBAC

Todas las peticiones del frontend o clientes externos ingresan a través del `api-gateway` (puerto `8080`), el cual valida los tokens JWT de Keycloak y aplica los roles:

| Método | Endpoint | Rol RBAC Requerido | Descripción |
|:---:|:---|:---:|:---|
| `POST` | `/api/v1/reservas` | `ROLE_TURISTA`, `ROLE_ADMIN` | Crea una reserva ejecutando toda la orquestación. Devuelve `201 Created`. |
| `GET` | `/api/v1/reservas` | `ROLE_ADMIN` | Lista la totalidad de las reservas del sistema (auditoría global). |
| `GET` | `/api/v1/reservas/mis-reservas` | `ROLE_TURISTA`, `ROLE_ADMIN` | Lista las reservas del turista logueado (parámetro opcional `turistaId`, default `turista1`). |
| `GET` | `/api/v1/reservas/{id}` | `ROLE_TURISTA`, `ROLE_OPERADOR`, `ROLE_ADMIN` | Detalle completo de una reserva por su ID. |
| `GET` | `/api/v1/reservas/actividad/{actividadId}` | `ROLE_OPERADOR`, `ROLE_ADMIN` | Lista todas las reservas recibidas para una excursión. |
| `PUT` | `/api/v1/reservas/{id}/confirmar` | `ROLE_OPERADOR`, `ROLE_ADMIN` | Confirma una reserva en estado `PENDIENTE`. |
| `PUT` | `/api/v1/reservas/{id}/cancelar` | `ROLE_TURISTA`, `ROLE_ADMIN` | Cancela una reserva (valida que no esté previamente cancelada o rechazada). |

---

## ⚙️ 4. Configuración y Propiedades

En [`src/main/resources/application.properties`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/ms-reservas/src/main/resources/application.properties), bajo estándar 12-Factor App:

```properties
server.port=8082
spring.application.name=ms-reservas

# Base de datos: H2 en memoria para desarrollo local / PostgreSQL en Docker
spring.datasource.url=jdbc:h2:mem:reservasdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update

# URLs de Microservicios Colaboradores
servicios.catalogo.url=${MS_CATALOGO_URL:http://localhost:8081/api/v1/catalogo}
servicios.geoapi.url=${GEOAPI_URL:http://localhost:8083/api/v1/geo}
servicios.openmeteo.url=https://api.open-meteo.com/v1/forecast

# Servidor SMTP de Notificaciones (Mailpit)
spring.mail.host=${SPRING_MAIL_HOST:localhost}
spring.mail.port=${SPRING_MAIL_PORT:1025}
spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false
```

---

## 🌐 5. Especificación de Contratos JSON

### 5.1. Crear Reserva (`POST /api/v1/reservas`)

#### Payload de Solicitud (Request Body):
```json
{
  "actividadId": 1,
  "fechaExcursion": "2026-11-20",
  "cantidadPersonas": 2,
  "emailTurista": "turista1@example.com",
  "origenTurista": {
    "latitud": -31.4135,
    "longitud": -64.18105,
    "direccion": "Hotel Sheraton Córdoba, Duarte Quirós 1300"
  }
}
```
*(Nota: El campo `turistaId` es opcional; si no se especifica, toma por defecto `"turista1"`).*

#### Respuesta Exitosa (`201 Created`):
```json
{
  "id": 4,
  "turistaId": "turista1",
  "emailTurista": "turista1@example.com",
  "actividadId": 1,
  "tituloActividad": "Ascenso al Cerro Champaquí",
  "fechaExcursion": "2026-11-20",
  "cantidadPersonas": 2,
  "montoTotal": 90000.0,
  "estado": "PENDIENTE",
  "trasladoEstimado": {
    "distanciaKm": 164.63,
    "duracionFormateada": "3 hs 2 min"
  },
  "alertaClimatica": false,
  "descripcionClima": "Probabilidad de lluvia: 15%, código de clima: 1, temperatura máxima: 24.5 °C",
  "fechaCreacion": "2026-11-12T16:00:00"
}
```

---

## 🏗️ 6. Arquitectura y Estructura del Código

```text
ms-reservas/src/main/java/ms_reservas/
├── MsReservasApplication.java
├── config/
│   ├── RestClientConfig.java       # Bean central RestClient con timeouts de conexión y lectura
│   └── DataLoader.java             # Precarga de reservas de prueba (CommandLineRunner)
├── controllers/
│   └── ReservaController.java      # Endpoints REST expuestos con validaciones @Valid
├── entities/
│   ├── Reserva.java                # Entidad JPA (@Table(name = "reservas"))
│   └── EstadoReserva.java          # Enum (PENDIENTE, CONFIRMADA, CANCELADA, RECHAZADA_POR_CLIMA)
├── repositories/
│   └── ReservaRepository.java      # Repositorio JPA con queries de cupo por fecha
├── dtos/
│   ├── request/
│   │   ├── ReservaRequestDTO.java  # DTO de entrada con validaciones Jakarta
│   │   └── OrigenTuristaDTO.java   # Coordenadas y dirección del turista
│   ├── response/
│   │   ├── ReservaResponseDTO.java # Respuesta enriquecida para el cliente
│   │   └── TrasladoEstimadoDTO.java# Distancia y duración calculadas por OSRM
│   └── external/                   # Contratos de integración inter-servicio
│       ├── ActividadCatalogoDTO.java
│       ├── RutaGeoRequestDTO.java
│       ├── RutaGeoResponseDTO.java
│       └── OpenMeteoResponseDTO.java
├── clients/                        # Clientes HTTP desacoplados vía RestClient
│   ├── CatalogoClient.java         # Comunicación sincrónica con ms-catalogo (8081)
│   ├── GeoApiClient.java           # Comunicación sincrónica con geoapi (8083)
│   └── OpenMeteoClient.java        # Consulta con resiliencia/fallback a Open-Meteo
├── exceptions/
│   ├── BadRequestException.java    # Error 400 por reglas de negocio violadas
│   ├── ResourceNotFoundException.java # Error 404 por recursos inexistentes
│   └── GlobalExceptionHandler.java # Interceptor @RestControllerAdvice con ErrorResponseDTO
└── services/
    ├── ReservaService.java         # Interfaz de contrato de negocio
    ├── EmailService.java           # Interfaz de despacho de notificaciones
    └── impl/
        ├── ReservaServiceImpl.java # Orquestador central de reservas
        └── EmailServiceImpl.java   # Despacho de emails SMTP por JavaMailSender
```

---

## 🐳 7. Despliegue con Docker y Base de Datos

### 1. Inicialización en PostgreSQL:
Se incluye el script [`init-db/01-init.sql`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/init-db/01-init.sql) montado en `/docker-entrypoint-initdb.d` para que la instancia de Postgres cree automáticamente la base `reservasdb` junto a `catalogodb`.

### 2. Iniciar con Docker Compose:
```powershell
docker compose up --build -d
```
Verifica que los servicios queden en estado `healthy`/`running`:
* `postgres-db`: `5432`
* `mailpit`: `1025` (SMTP) / `8025` (Web UI en **http://localhost:8025**)
* `keycloak`: `8180`
* `ms-catalogo`: `8081`
* `ms-reservas`: `8082`
* `api-gateway`: `8080`

### 3. Ejecución Local Independiente:
```powershell
mvn clean package -DskipTests -pl ms-reservas
java -jar ms-reservas/target/ms-reservas-1.0.0-SNAPSHOT.jar
```
* Acceso a Swagger UI: 👉 **[http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)**
* Consola H2: 👉 **[http://localhost:8082/h2-console](http://localhost:8082/h2-console)** (JDBC URL: `jdbc:h2:mem:reservasdb`, Usuario: `sa`, Contraseña en blanco).

---

## 🧪 8. Pruebas y Validación (Postman y cURL)

La colección oficial [`tpi_postman_collection.json`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/tpi_postman_collection.json) incluye la carpeta completa `2. Microservicio Reservas (ms-reservas)` lista para ejecutar en orden:

### Flujo de Prueba Sugerido:
1. **0.1 Login Turista:** Obtiene el token JWT y lo almacena automáticamente en `{{token}}`.
2. **2.1.1 Crear Reserva:** Envía la solicitud con `emailTurista: "turista1@example.com"`. Recibe `201 Created` en estado `PENDIENTE`, calculando la distancia y despachando el correo a Mailpit.
3. **Revisar Mailpit:** Abrir en el navegador `http://localhost:8025` para constatar la llegada del email con el tiempo estimado de viaje.
4. **2.2.2 Confirmar Reserva (Operador):** Invoca `PUT /api/v1/reservas/1/confirmar` con token de operador; la reserva pasa a `CONFIRMADA`.
5. **2.1.3 Cancelar Reserva (Turista):** Invoca `PUT /api/v1/reservas/1/cancelar`; la reserva pasa a `CANCELADA`. Si se intenta cancelar nuevamente, responde `400 Bad Request`.
6. **2.3.2 Listar Todas las Reservas (Admin):** Invoca `GET /api/v1/reservas` con token de admin; lista todas las reservas del sistema.
