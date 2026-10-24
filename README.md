# TPI Backend - Sistema de Gestión de Actividades Turísticas y Excursiones
**Backend de Aplicaciones — UTN FRC (2026)**

Este repositorio contiene la arquitectura de microservicios diseñada e implementada para la gestión integral de actividades turísticas, excursiones al aire libre, cálculo de rutas geográficas y reservas de usuarios.

---

## 🏗️ 1. Arquitectura General del Sistema

El sistema sigue los lineamientos del Trabajo Práctico Integrador (TPI):

* **API Gateway (`api-gateway`)**: Punto único de entrada al sistema, responsable del enrutamiento dinámico, resolución de CORS y validación de seguridad mediante filtro JWT con Keycloak.
* **Microservicio de Catálogo (`ms-catalogo`)**: Administración de excursiones, actividades, categorías, dificultades, cupos máximos y coordenadas de puntos de encuentro.
* **Microservicio de Reservas (`ms-reservas`)**: Gestión de reservas, cálculo de tarifas, estado de confirmación y orquestación con otros servicios.
* **Microservicio Geoespacial (`geoapi`)**: Cálculo de rutas terrestres, distancias y tiempos estimados mediante motor local **OSRM (Open Source Routing Machine)**.
* **Librería Común (`common-lib`)**: Artefacto Maven compartido (JAR) con DTOs transversales, excepciones estándar de negocio y utilidades de seguridad.
* **Identity Provider (Keycloak)**: Servidor de autenticación y autorización basado en OIDC y roles (`ADMIN`, `OPERADOR`, `TURISTA`).
* **Integración con API Externa (Requisito TPI)**: Integración con **Open-Meteo API** (servicio público de meteorología) para consultar pronósticos y emitir alertas climáticas que condicionen la confirmación de excursiones al aire libre.

---

## 👥 2. Roles y Permisos (Keycloak RBAC)

| Rol | Descripción | Capacidades |
|---|---|---|
| `TURISTA` | Usuario final cliente | Explorar catálogo, consultar distancias desde su origen, cotizar, crear y cancelar sus propias reservas. |
| `OPERADOR` | Guía o empresa turística | Publicar y modificar actividades, definir cupos, puntos de encuentro e itinerarios. Consultar reservas de sus excursiones. |
| `ADMIN` | Administrador del sistema | Acceso total: gestión de categorías, auditoría, anulación forzada, métricas de negocio. |

---

## 🌐 3. Mapa de Endpoints por Microservicio

Todas las peticiones externas ingresan a través del **API Gateway** (`http://localhost:8080`).

---

### 🟢 3.1. API Gateway (`api-gateway`)
* **Puerto local directo:** `8080`
* **Ruteo:** Reenvía el tráfico con prefijos `/api/v1/...` hacia el microservicio correspondiente, validando el token Bearer JWT en las rutas protegidas.

---

### 🧭 3.2. Microservicio de Catálogo (`ms-catalogo`)
* **Responsabilidad:** Administrar las actividades turísticas, categorías y sus puntos de encuentro.
* **Puerto interno:** `8081` | **Ruta pública Gateway:** `/api/v1/catalogo`

#### Endpoints ordenados por tipo de petición:

| Método | Endpoint Gateway | Rol Mínimo | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/catalogo/actividades` | Público | Lista actividades con filtros opcionales (`categoria`, `dificultad`, `precioMax`, `ciudad`). |
| `GET` | `/api/v1/catalogo/actividades/{id}` | Público | Obtiene el detalle completo de una actividad (descripción, coordenadas de inicio, cupo máximo). |
| `GET` | `/api/v1/catalogo/actividades/{id}/disponibilidad` | Público | Consulta si hay cupos disponibles para una fecha específica (usado por reservas). |
| `GET` | `/api/v1/catalogo/categorias` | Público | Lista de categorías de excursiones (Trekking, Navegación, Cabalgata, etc.). |
| `POST` | `/api/v1/catalogo/actividades` | `OPERADOR`, `ADMIN` | Crea una nueva actividad turística con sus datos geográficos y cupos. |
| `POST` | `/api/v1/catalogo/categorias` | `ADMIN` | Crea una nueva categoría. |
| `PUT` | `/api/v1/catalogo/actividades/{id}` | `OPERADOR`, `ADMIN` | Actualiza la información y condiciones de una actividad existente. |
| `DELETE` | `/api/v1/catalogo/actividades/{id}` | `ADMIN` | Da de baja lógica una actividad turística. |

#### Ejemplo Payload `POST /api/v1/catalogo/actividades`:
```json
{
  "titulo": "Trekking al Champaquí",
  "descripcion": "Ascenso al cerro más alto de Córdoba.",
  "categoriaId": 1,
  "dificultad": "ALTA",
  "precioBase": 45000.0,
  "cupoMaximo": 15,
  "latitudInicio": -31.9868,
  "longitudInicio": -64.9351,
  "direccionPuntoEncuentro": "Villa Alpina, Córdoba",
  "requiereBuenClima": true
}
```

---

### 🎫 3.3. Microservicio de Reservas (`ms-reservas`)
* **Responsabilidad:** Gestionar el ciclo de vida de las reservas y orquestar reglas de negocio inter-servicio.
* **Puerto interno:** `8082` | **Ruta pública Gateway:** `/api/v1/reservas`

#### Endpoints ordenados por tipo de petición:

| Método | Endpoint Gateway | Rol Mínimo | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/reservas/mis-reservas` | `TURISTA` | Consulta las reservas del usuario autenticado (extraído del token JWT). |
| `GET` | `/api/v1/reservas/{id}` | `TURISTA`, `OPERADOR` | Obtiene el detalle de una reserva específica (con control de acceso por usuario). |
| `GET` | `/api/v1/reservas/actividad/{actividadId}` | `OPERADOR`, `ADMIN` | Lista todas las reservas asociadas a una excursión para organizar el contingente. |
| `POST` | `/api/v1/reservas` | `TURISTA` | Crea una nueva reserva. Ejecuta la validación de negocio inter-servicio. |
| `PUT` | `/api/v1/reservas/{id}/confirmar` | `OPERADOR`, `ADMIN` | Confirma la reserva una vez acreditado el pago o autorizada la actividad. |
| `PUT` | `/api/v1/reservas/{id}/cancelar` | `TURISTA`, `ADMIN` | Cancela una reserva existente según políticas de anticipación horaria. |

#### Ejemplo Payload `POST /api/v1/reservas`:
```json
{
  "actividadId": 12,
  "fechaExcursion": "2026-11-15",
  "cantidadPersonas": 3,
  "origenTurista": {
    "latitud": -31.4135,
    "longitud": -64.18105,
    "direccion": "Hotel Sheraton, Córdoba Capital"
  }
}
```

#### Ejemplo Respuesta `201 Created`:
```json
{
  "reservaId": 1045,
  "estado": "CONFIRMADA",
  "montoTotal": 135000.0,
  "fechaExcursion": "2026-11-15",
  "trasladoEstimado": {
    "distanciaKm": 118.4,
    "duracionEstimada": "2 hs 15 min",
    "horaRecomendadaSalida": "06:45 AM"
  },
  "pronosticoClima": {
    "condicion": "Despejado",
    "temperaturaMax": 24.5,
    "probabilidadLluvia": 10,
    "alertaClimatica": false
  }
}
```

---

### 🗺️ 3.4. Microservicio Geoespacial (`geoapi`)
* **Responsabilidad:** Cálculos de rutas, tiempos de viaje terrestre y distancias entre puntos geográficos mediante OSRM.
* **Puerto interno:** `8083` | **Ruta pública Gateway:** `/api/v1/geo`

#### Endpoints ordenados por tipo de petición:

| Método | Endpoint Gateway | Rol Mínimo | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/geo/rutas/estimacion` | Autenticado | Versión rápida por QueryParams (`?latOrig=...&lonOrig=...&latDest=...&lonDest=...`). |
| `GET` | `/api/v1/geo/health-osrm` | `ADMIN` | Chequeo de conectividad y estado del motor local OSRM. |
| `POST` | `/api/v1/geo/rutas/distancia` | Autenticado | Calcula distancia exacta y tiempo estimado entre origen y destino terrestre. |

#### Ejemplo Payload `POST /api/v1/geo/rutas/distancia`:
```json
{
  "origen": {
    "latitud": -31.4135,
    "longitud": -64.18105
  },
  "destino": {
    "latitud": -31.9868,
    "longitud": -64.9351
  }
}
```

#### Ejemplo Respuesta `200 OK`:
```json
{
  "distanciaMetros": 128450.0,
  "distanciaKm": 128.45,
  "duracionSegundos": 8100,
  "duracionMinutos": 135,
  "duracionFormateada": "2 hs 15 min"
}
```

---

## 🔄 4. Proceso de Negocio Integrador (Colaboración entre Microservicios)

En cumplimiento estricto con el enunciado (*"Al menos un proceso significativo de negocio deberá involucrar la participación de más de un microservicio funcional"*):

```
       [Cliente / Turista]
               │
               ▼  POST /api/v1/reservas
        [api-gateway] (Filtro JWT Keycloak)
               │
               ▼
        [ms-reservas] (Servicio Orquestador)
         ├── 1. Llama a [ms-catalogo]: GET /actividades/{id} (valida existencia, precio y cupos).
         ├── 2. Llama a [geoapi]: POST /rutas/distancia (calcula tiempo y distancia de traslado).
         └── 3. Llama a [Open-Meteo API Externa]: GET /v1/forecast (consulta clima en la fecha).
               │
               ▼  Regla de negocio:
                  - Si hay cupo suficiente Y no hay alerta meteorológica severa:
                    Registra la reserva, descuenta cupos y devuelve el itinerario completo.
```

---

## ☁️ 5. Integración con API Pública Externa (Requisito TPI)

* **Servicio Seleccionado:** **Open-Meteo Weather API** (`https://api.open-meteo.com/v1/forecast`).
* **Justificación de Negocio:** Las actividades turísticas (como trekking, navegación o cabalgatas) dependen críticamente de las condiciones meteorológicas. Consultar el pronóstico por coordenadas de la excursión permite alertar o rechazar automáticamente reservas en días de tormenta o clima adverso, aportando seguridad al turista.
* **Manejo de Fallas (Resiliencia):** Si la API externa no está disponible, se aplica un mecanismo de *fallback* (Circuit Breaker / Timeout), permitiendo la reserva en estado condicional con advertencia al usuario.

---

## 📦 6. Componente Común (`common-lib`)

Biblioteca compartida empaquetada como JAR en `.m2` que contiene:
* `GlobalExceptionHandler`: Manejo uniforme de errores (`400 Bad Request`, `404 Not Found`, `409 Conflict`, `500 Internal Server`).
* `ErrorResponseDTO`: Estructura unificada para mensajes de error: `{ "timestamp", "status", "error", "mensaje", "path" }`.
* `SecurityUtils`: Extracción de `userId`, `email` y `roles` del token JWT de Keycloak.

---

## 🚀 7. Instrucciones para Ejecución Local

### Paso 1: Levantar infraestructura base (Docker)
```powershell
# Motor de rutas OSRM
docker compose -f docker-compose.osrm.yml up -d
```

### Paso 2: Compilar e instalar `common-lib`
```powershell
cd common-lib
mvn clean install
cd ..
```

### Paso 3: Ejecutar los microservicios
```powershell
# En terminales separadas o mediante perfiles de Spring Boot:
mvn -pl geoapi spring-boot:run
mvn -pl ms-catalogo spring-boot:run
mvn -pl ms-reservas spring-boot:run
mvn -pl api-gateway spring-boot:run
```

### Documentación Interactiva (Swagger UI):
* **API Gateway (agrupado):** `http://localhost:8080/swagger-ui.html`
* **Catálogo:** `http://localhost:8081/swagger-ui.html`
* **Reservas:** `http://localhost:8082/swagger-ui.html`
* **GeoAPI:** `http://localhost:8083/swagger-ui.html`
