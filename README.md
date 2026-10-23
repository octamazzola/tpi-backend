# TPI Backend - Servicio de Rutas y Geolocalización (OSRM + GeoAPI)

Módulo encargado del cálculo de distancias y tiempos estimados de viaje terrestre en Argentina para el **Sistema de Gestión de Actividades Turísticas y Excursiones**.

Reemplaza la integración con la API de Google Maps por una solución **local, gratuita y sin límites de cuota** basada en **OSRM (Open Source Routing Machine)** en Docker, consumida por un microservicio en **Spring Boot (`geoapi`)**.

---

## 📋 Requisitos Previos

* [Docker Desktop](https://www.docker.com/) instalado y en ejecución.
* **Java 21** o superior.

---

## 🚀 Guía de Puesta en Marcha

### 1. Datos del Mapa (`osrm-data`)
> ⚠️ **Importante:** Por limitaciones de tamaño de GitHub (+4 GB), los datos preprocesados del mapa no se versionan en el repositorio.

* **Opción A (Recomendada - Descarga directa):**
  1. Descargá el archivo comprimido desde el siguiente enlace del equipo:  
     👉 **[Descargar osrm-data (Google Drive)](https://drive.google.com/file/d/1KaBt7SngvWpoHdeLkjdckU9Nw5lSn9Yi/view?usp=sharing)**
  2. Descomprimí el contenido y asegurate de que la carpeta quede en la raíz del repositorio con el nombre:
     ```text
     tpi-backend/
     └── osrm-data/
         ├── argentina-latest.osrm
         ├── argentina-latest.osrm.hsgr
         └── ...
     ```

* **Opción B (Generación manual desde cero):**
  Solo si preferís regenerar los datos en tu computadora (~25 minutos de procesamiento):
  ```powershell
  mkdir osrm-data; cd osrm-data
  curl.exe -O https://download.geofabrik.de/south-america/argentina-latest.osm.pbf
  docker run -t -v "${PWD}:/data" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/argentina-latest.osm.pbf
  docker run -t -v "${PWD}:/data" osrm/osrm-backend osrm-contract /data/argentina-latest.osrm
  cd ..
  ```

---

### 2. Levantar el Contenedor OSRM (Docker)
En la raíz de `tpi-backend`:

```powershell
docker compose -f docker-compose.osrm.yml up -d
```

Podés verificar que el servicio esté respondiendo en el puerto `5000`:
```powershell
curl.exe "http://localhost:5000/route/v1/driving/-64.18105,-31.4135;-60.6985,-32.9471?overview=false"
```

---

### 3. Levantar el Microservicio Spring Boot (`geoapi`)
Desde la carpeta raíz:

```powershell
cd geoapi
.\mvnw.cmd spring-boot:run
```
*(En Linux/macOS: `./mvnw spring-boot:run`)*

* **Swagger UI:** `http://localhost:8080/swagger-ui/index.html` (o el puerto configurado).
* **Motor OSRM directo:** `http://localhost:5000`

---

## 📌 Estado de la Implementación y Próximos Pasos

### ✅ Ya Implementado
- [x] Contenedor Docker de OSRM con el mapa de Argentina preprocesado y probado (CH profile).
- [x] Configuración de Docker Compose (`docker-compose.osrm.yml`) para ejecución nativa en x86_64.
- [x] Configuración de `.gitignore` para excluir datos pesados de mapas y artefactos de compilación.
- [x] Proyecto base de Spring Boot (`geoapi`) con Maven Wrapper y dependencias (Spring Web, Validation, Actuator, Springdoc OpenAPI / Swagger UI 2.5.0).

---

### ⏳ Pendiente de Implementar en `geoapi`

Para que el microservicio esté 100% completo y listo para integrarse con `ms-catalogo` y `ms-reservas`, se deben implementar los siguientes componentes:

1. **Configuración de OSRM en `application.yml`**:
   * Definir la propiedad `osrm.base-url: http://localhost:5000` y el puerto del microservicio (ej. `8083`).
2. **DTOs de Entrada y Salida (Modelos)**:
   * **`RutaRequest`**: Coordenadas de origen (`latitudOrigen`, `longitudOrigen`) y destino (`latitudDestino`, `longitudDestino`).
   * **`RutaResponse`**: Información limpia para el negocio: `distanciaMetros`, `distanciaKm`, `duracionSegundos`, `duracionMinutos`, `duracionFormateada` ("X hs Y min").
   * **DTOs internos de OSRM**: Clases para deserializar el JSON devuelto por OSRM (`code`, `routes`, `legs`, `distance`, `duration`).
3. **Servicio Cliente (`OsrmService`)**:
   * Implementación usando `RestClient` de Spring Boot 3 para invocar `GET /route/v1/driving/{coords}`.
   * Lógica de conversión de unidades y redondeo.
   * Manejo de excepciones y respuestas de contingencia si OSRM no estuviese disponible o las coordenadas no estuvieran en ruta.
4. **Controlador REST (`RutaController`)**:
   * Endpoint documentado (ej. `POST /api/v1/rutas/distancia` o `GET /api/v1/rutas/distancia`).
   * Anotaciones Swagger/OpenAPI (`@Tag`, `@Operation`, `@ApiResponse`) para validación y pruebas directas desde la UI web.
