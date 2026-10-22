# TPI Backend - Servicio de Rutas y Geolocalización (OSRM + GeoAPI)

Este módulo provee el cálculo de distancias y tiempos de traslado terrestre en Argentina utilizando **OSRM (Open Source Routing Machine)** en Docker y expuesto a través de un microservicio **Spring Boot (GeoAPI)**.

---

## 📋 Requisitos Previos

* [Docker Desktop](https://www.docker.com/) instalado y en ejecución.
* **Java 21** o superior.

---

## 🚀 Guía de Puesta en Marcha

### 1. Datos del Mapa (`osrm-data`)
> ⚠️ **Importante:** Por limitaciones de tamaño de GitHub (+4 GB), la carpeta `osrm-data/` no se sube al repositorio.

Tenés dos opciones para obtenerla:
* **Opción A (Recomendada):** Pedirle la carpeta `osrm-data` ya procesada a un integrante del equipo y pegarla en la raíz de `tpi-backend`.
* **Opción B (Generación manual desde cero):**
  1. Crear carpeta `osrm-data` y descargar el mapa de Argentina:
     ```powershell
     mkdir osrm-data; cd osrm-data
     # Descargar argentina-latest.osm.pbf desde Geofabrik
     curl.exe -O https://download.geofabrik.de/south-america/argentina-latest.osm.pbf
     ```
  2. Procesar el mapa (dentro de `osrm-data`):
     ```powershell
     docker run -t -v "${PWD}:/data" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/argentina-latest.osm.pbf
     docker run -t -v "${PWD}:/data" osrm/osrm-backend osrm-contract /data/argentina-latest.osrm
     cd ..
     ```

---

### 2. Levantar el Contenedor de OSRM (Docker)
En la raíz de `tpi-backend`:

```powershell
docker compose -f docker-compose.osrm.yml up -d
```

Verificar que esté funcionando en el puerto 5000:
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

---

### 4. Documentación y Pruebas
Una vez levantado el microservicio:
* **Swagger UI:** `http://localhost:8080/swagger-ui/index.html` (o el puerto configurado).
* **OSRM directo:** `http://localhost:5000`
