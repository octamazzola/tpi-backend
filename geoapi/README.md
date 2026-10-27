# 🗺️ Microservicio Geoespacial (`geoapi`)
**Backend de Aplicaciones — UTN FRC (2026)**  
**Dominio:** Sistema de Gestión de Actividades Turísticas y Excursiones  
**Tecnología:** Java 21, Spring Boot 3.3.5, SpringDoc OpenAPI 2.5.0, OSRM (Docker)

---

## 📋 1. Descripción y Responsabilidad

El microservicio `geoapi` es el componente encargado de los **cálculos geoespaciales, estimación de distancias terrestres y tiempos de traslado** para las excursiones turísticas del sistema.

### Responsabilidades Clave:
* **Cálculo de Rutas Terrestres Reales:** Se comunica vía `RestClient` con un motor local **OSRM (Open Source Routing Machine)** montado sobre la red vial de Argentina (`argentina-latest.osrm`).
* **Soporte a la Orquestación de Reservas:** Permite a `ms-reservas` calcular distancias y tiempos de viaje entre la ubicación del turista (hotel/origen) y el punto de encuentro de la excursión.
* **Separated Interface (SOLID - DIP):** Implementa el principio de Inversión de Dependencias desacoplando la interfaz de contrato (`RutaService`) de su implementación concreta (`RutaServiceImpl`).
* **Manejo Centralizado de Excepciones y Resiliencia:** Transforma respuestas y fallos en códigos HTTP semánticos (`200 OK`, `400 Bad Request`, `502 Bad Gateway`).
* **Auditoría de Salud (Health Check):** Monitorea continuamente la disponibilidad del contenedor Docker de OSRM.

---

## 🗺️ 2. Mapa de Rutas y Compatibilidad con API Gateway

El microservicio admite tanto el consumo directo en el puerto `8083` como el consumo ruteado a través del `api-gateway` en el puerto `8080`:

| Método | Ruta en GeoAPI (Directo `8083`) | Ruta vía API Gateway (`8080`) | Rol RBAC Requerido | Descripción |
|:---:|:---|:---|:---:|:---|
| `POST` | `/api/v1/geo/rutas/distancia` o `/api/v1/rutas/distancia` | `/api/v1/geo/rutas/distancia` | Autenticado (`ROLE_TURISTA`, `ROLE_OPERADOR`, `ROLE_ADMIN`) | Cálculo completo de trayecto con coordenadas anidadas. |
| `GET` | `/api/v1/geo/rutas/estimacion` o `/api/v1/rutas/estimacion` | `/api/v1/geo/rutas/estimacion` | Autenticado | Estimación rápida mediante Query Parameters. |
| `GET` | `/api/v1/geo/health-osrm` o `/api/v1/health-osrm` | `/api/v1/geo/health-osrm` | Autenticado | Chequeo de conectividad con el contenedor OSRM. |
| `GET` | `/swagger-ui.html` / `/v3/api-docs` | — | Público | Documentación interactiva OpenAPI 3.0. |

---

## ⚙️ 3. Configuración y Propiedades

En [`src/main/resources/application.yml`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/geoapi/src/main/resources/application.yml), configurado bajo el estándar 12-Factor:

```yaml
server:
  port: ${SERVER_PORT:8083}

osrm:
  base-url: ${OSRM_BASE_URL:http://localhost:5000}

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

* **Timeouts de Conexión:** Configurados en [`RestClientConfig.java`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/geoapi/src/main/java/utnfc/isi/back/spring/geoapi/config/RestClientConfig.java) con 5 segundos para conexión y 15 segundos de lectura.
* **Cabecera de Codificación:** Envío forzado de `Accept-Encoding: identity` para prevenir excepciones de descompresión con servidores HTTP/1.0.

---

## 🌐 4. Especificación de Endpoints y Contratos Oficiales

### 4.1. Calcular Ruta Terrestre (`POST /api/v1/geo/rutas/distancia`)

Recibe el **formato anidado oficial** del proyecto y Postman, validando latitud (`[-90.0, 90.0]`) y longitud (`[-180.0, 180.0]`).

#### Contrato JSON de Petición (Request Body):
```json
{
  "origen": {
    "latitud": -31.41,
    "longitud": -64.18
  },
  "destino": {
    "latitud": -31.98,
    "longitud": -64.93
  }
}
```

#### Contrato JSON de Respuesta Exitosa (`200 OK`):
```json
{
  "distanciaMetros": 163934.8,
  "distanciaKm": 163.93,
  "duracionSegundos": 10848,
  "duracionMinutos": 181,
  "duracionFormateada": "3 hs 1 min"
}
```

#### Respuestas de Error:
* **`400 Bad Request`**: Si faltan campos requeridos o las coordenadas exceden los rangos geográficos.
* **`502 Bad Gateway`**: Si el motor OSRM no está accesible o no existe conexión vial terrestre entre ambos puntos.

---

### 4.2. Estimación Rápida por Query Params (`GET /api/v1/geo/rutas/estimacion`)

Permite consultas ágiles sin payload en el body:
```http
GET /api/v1/geo/rutas/estimacion?latOrig=-31.41&lonOrig=-64.18&latDest=-31.98&lonDest=-64.93
```

#### Respuesta (`200 OK`):
```json
{
  "distanciaMetros": 164633.8,
  "distanciaKm": 164.63,
  "duracionSegundos": 10931,
  "duracionMinutos": 182,
  "duracionFormateada": "3 hs 2 min"
}
```

---

### 4.3. Health Check del Motor OSRM (`GET /api/v1/geo/health-osrm`)

Audita si OSRM está activo en Docker y respondiendo rutas de prueba:

#### Respuesta (`200 OK` - OSRM Activo):
```json
{
  "status": "UP",
  "osrmUrl": "http://localhost:5000",
  "detalle": "Motor OSRM respondiendo correctamente en Docker"
}
```

#### Respuesta (`200 OK` - OSRM Caído):
```json
{
  "status": "DOWN",
  "osrmUrl": "http://localhost:5000",
  "detalle": "Fallo de conexión con OSRM: I/O error on GET request..."
}
```

---

## 🔒 5. Integración con API Gateway y Seguridad RBAC

Cuando las peticiones ingresan a través del `api-gateway` (`http://localhost:8080/api/v1/geo/**`):
1. **Validación JWT:** El Gateway verifica la validez del token emitido por Keycloak (`turismo-realm` / `tpi-realm`).
2. **Control de Acceso:** Requiere cualquier usuario autenticado (`ROLE_TURISTA`, `ROLE_OPERADOR` o `ROLE_ADMIN`).
3. **Respuestas de Seguridad:**
   * Sin token $\rightarrow$ `401 Unauthorized` descriptivo en JSON.
   * Token inválido/expirado $\rightarrow$ `401 Unauthorized`.
4. **Reenvío:** El Gateway elimina los encabezados sensibles internos y redirige la petición a `http://localhost:8083`.

---

## 🏗️ 6. Arquitectura y Estructura del Código Fuente

Sigue Clean Architecture y principios SOLID:

```text
geoapi/src/main/java/utnfc/isi/back/spring/geoapi/
├── config/
│   ├── OsrmProperties.java         # Propiedades de enlace con OSRM (@ConfigurationProperties)
│   └── RestClientConfig.java       # Bean RestClient con timeouts y headers estándar
├── controller/
│   └── RutaController.java         # Endpoints REST expuestos con anotaciones OpenAPI 3.0
├── dto/
│   ├── CoordenadaDTO.java          # Record { latitud, longitud } con validaciones Bean Validation
│   ├── RutaRequest.java            # Contrato oficial único: { origen: {...}, destino: {...} }
│   ├── RutaResponse.java           # Record inmutable de respuesta de distancias y tiempos
│   └── OsrmStatusDTO.java          # DTO de diagnóstico de salud de OSRM
├── exception/
│   ├── GlobalExceptionHandler.java # @RestControllerAdvice para 400 y 502
│   └── OsrmUnavailableException.java# Excepción unchecked de fallo de comunicación
├── osrm/
│   └── OsrmRouteResponse.java      # Mapeo Jackson de la respuesta nativa de OSRM
└── service/
    ├── RutaService.java            # Separated Interface (DIP): Contrato abstracto
    ├── OsrmService.java            # Clase delegada para compatibilidad
    └── impl/
        └── RutaServiceImpl.java    # Implementación desacoplada y mapeo lon/lat
```

---

## 🐳 7. Despliegue con Docker y OSRM

### 1. Iniciar OSRM con Docker Compose:
Asegúrate de tener descargado el mapa en `osrm-data/argentina-latest.osrm` y levantar el contenedor:
```powershell
docker compose -f docker-compose.osrm.yml up -d
```
Verificar que responda en el puerto 5000:
```powershell
curl.exe http://localhost:5000/route/v1/driving/-64.18,-31.41;-64.93,-31.98?overview=false
```

### 2. Compilar GeoAPI con Maven:
```powershell
mvn clean package -DskipTests -f geoapi/pom.xml
```

### 3. Ejecutar el Microservicio:
```powershell
java -jar geoapi/target/geoapi-0.0.1-SNAPSHOT.jar
```
* Acceso a Swagger: 👉 **[http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)**

### 4. Construir la Imagen Docker de GeoAPI:
Utiliza [`geoapi/Dockerfile`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/geoapi/Dockerfile):
```powershell
docker build -t tpi-geoapi:1.0.0 -f geoapi/Dockerfile geoapi/
```

---

## 🧪 8. Pruebas y Validación (cURL y Postman)

### Consumo Directo a GeoAPI (Puerto 8083):
```powershell
# 1. Distancia con JSON anidado oficial
curl.exe -X POST http://localhost:8083/api/v1/geo/rutas/distancia `
  -H "Content-Type: application/json" `
  -d '{ "origen": { "latitud": -31.41, "longitud": -64.18 }, "destino": { "latitud": -31.98, "longitud": -64.93 } }'

# 2. Estimación rápida
curl.exe "http://localhost:8083/api/v1/geo/rutas/estimacion?latOrig=-31.41&lonOrig=-64.18&latDest=-31.98&lonDest=-64.93"

# 3. Health Check OSRM
curl.exe http://localhost:8083/api/v1/geo/health-osrm
```

### Consumo a través del API Gateway (Puerto 8080 con Token):
```powershell
# Obtener Token de Keycloak (ej. turista1)
$auth = curl.exe -s -X POST "http://localhost:8180/realms/tpi-realm/protocol/openid-connect/token" `
  -d "client_id=tpi-client" -d "grant_type=password" -d "username=turista1" -d "password=1234" | ConvertFrom-Json
$token = $auth.access_token

# Invocar vía API Gateway
curl.exe -X POST http://localhost:8080/api/v1/geo/rutas/distancia `
  -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d '{ "origen": { "latitud": -31.41, "longitud": -64.18 }, "destino": { "latitud": -31.98, "longitud": -64.93 } }'
```

---

## 💡 9. Decisiones Técnicas y Gotchas Resueltos

1. **Orden de Coordenadas de OSRM:**  
   OSRM exige estrictamente `{longitud},{latitud};{longitud},{latitud}`. `RutaServiceImpl` realiza la inversión automática para que la API mantenga la convención habitual `{latitud, longitud}` sin confundir al consumidor.
2. **Prevención de URL-Encoding en Coordenadas:**  
   OSRM requiere comas (`,`) y puntos y comas (`;`) literales en la URL del recurso (`/route/v1/driving/-64.18,-31.41;-64.93,-31.98`). Spring `UriBuilder` codifica estos caracteres como `%2C` y `%3B`, lo cual OSRM rechaza con error 400. Se construyó la URI compuesta directamente en el método `.uri(...)` preservando la sintaxis requerida.
3. **Incompatibilidad GZIP HTTP/1.0:**  
   El backend C++ de OSRM responde en HTTP/1.0 sin compresión. Cuando los clientes envían compresión implícita, se producía un error `ZipException: incorrect header check`. Se resolvió configurando `SimpleClientHttpRequestFactory` con `Accept-Encoding: identity`.
4. **Alineación a Spring Boot 3.3.5:**  
   Se corrigió la versión del parent de Maven a `3.3.5`, garantizando compatibilidad total con `springdoc-openapi-starter-webmvc-ui:2.5.0` y Swagger UI.
