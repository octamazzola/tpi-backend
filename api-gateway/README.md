# 🚪 API Gateway (`api-gateway`)
**Backend de Aplicaciones — UTN FRC (2026)**  
**Dominio:** Sistema de Gestión de Actividades Turísticas y Excursiones

---

## 📋 1. Descripción y Responsabilidad

El componente `api-gateway` es el **punto único de entrada (Single Point of Contact)** para todas las peticiones externas provenientes de clientes, frontends (React / Angular / Mobile) o herramientas como Postman.

### Responsabilidades clave:
* **Enrutamiento Reactivo:** Basado en **Spring Cloud Gateway (WebFlux)** y configurado mediante Java DSL (`RouteLocatorBuilder`).
* **Abstracción de Topología:** Los clientes externos no necesitan conocer los puertos internos (`8081`, `8082`, `8083`) ni las IPs de los microservicios; todo se consume a través del puerto `8080`.
* **Soporte Global de CORS:** Habilita el consumo seguro desde navegadores web para cualquier origen frontend.
* **Security Filter (Keycloak):** valida los JWT, aplica RBAC por ruta y propaga la identidad del usuario a los microservicios (ver sección 4).
* **Swagger UI agrupado:** `http://localhost:8080/swagger-ui.html` muestra la documentación OpenAPI de los tres microservicios.

---

## 🗺️ 2. Mapa de Rutas Configurado

| Prefijo de Ruta Gateway | Destino / Microservicio | Variable de Entorno | Puerto Local por Defecto |
|---|---|---|---|
| `/api/v1/catalogo/**` | `ms-catalogo` | `MS_CATALOGO_URL` | `http://localhost:8081` |
| `/api/v1/reservas/**` | `ms-reservas` | `MS_RESERVAS_URL` | `http://localhost:8082` |
| `/api/v1/geo/**` | `geoapi` (OSRM) | `GEOAPI_URL` | `http://localhost:8083` |
| `/api-docs/catalogo` · `/api-docs/reservas` · `/api-docs/geoapi` | `/v3/api-docs` de cada servicio | — | (para el Swagger UI agrupado) |

---

## ⚙️ 3. Configuración y Propiedades

En [`application.properties`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/api-gateway/src/main/resources/application.properties):
* **Puerto:** `8080`
* **Parámetros configurables:** Las URLs se resuelven mediante el patrón 12-Factor `${VARIABLE:valor_por_defecto}`, permitiendo ejecutarse tanto en local (localhost) como dentro de la red privada de Docker (`http://ms-catalogo:8081`).
* **CORS Global:** Permite métodos `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.

---

## 🔒 4. Seguridad Reactiva y Roles RBAC (Keycloak)

El Gateway actúa como **OAuth2 Resource Server** validando tokens JWT stateless emitidos por Keycloak:
* **Conversor de Roles:** En [`SecurityConfig.java`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/api-gateway/src/main/java/api_gateway/config/SecurityConfig.java) se procesa `realm_access.roles` mapeándolo a `ROLE_TURISTA`, `ROLE_OPERADOR` y `ROLE_ADMIN`.
* **Reglas de Acceso:**
  * `GET /api/v1/catalogo/**` $\rightarrow$ Público (`.permitAll()`).
  * `POST/PUT/PATCH /api/v1/catalogo/actividades/**` $\rightarrow$ Requiere `ROLE_OPERADOR` o `ROLE_ADMIN`.
  * `DELETE /api/v1/catalogo/actividades/**` $\rightarrow$ Exclusivo `ROLE_ADMIN` (baja lógica).
  * `POST/DELETE /api/v1/catalogo/categorias/**` $\rightarrow$ Exclusivo `ROLE_ADMIN`.
  * `POST /api/v1/reservas` $\rightarrow$ Requiere `ROLE_TURISTA` o `ROLE_ADMIN`.
  * `GET /api/v1/reservas` y `POST /api/v1/reservas/revalidaciones` $\rightarrow$ Exclusivo `ROLE_ADMIN`.
  * `PUT /api/v1/reservas/*/confirmar` $\rightarrow$ `ROLE_OPERADOR` o `ROLE_ADMIN`; `PUT .../cancelar` $\rightarrow$ `ROLE_TURISTA` o `ROLE_ADMIN`.
  * `GET /api/v1/geo/health-osrm` $\rightarrow$ Exclusivo `ROLE_ADMIN`; resto de `/api/v1/geo/**` $\rightarrow$ usuario autenticado.
  * Swagger UI y `/api-docs/**` $\rightarrow$ Público (solo lectura de contratos).

### Propagación de identidad (`UserHeadersFilter`)
Filtro global que elimina los headers `X-User-*` enviados por el cliente y los vuelve a armar desde el JWT validado:
`X-User-Id` (`sub`), `X-User-Name` (`preferred_username`), `X-User-Email` (`email`) y `X-User-Roles`.
El contrato de nombres vive en `common-lib` (`UserHeaders`), compartido con los microservicios.

---

## 🐳 5. Despliegue con Docker

El servicio cuenta con su propio [`api-gateway/Dockerfile`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/api-gateway/Dockerfile) basado en `eclipse-temurin:21-jre-alpine` y está integrado en [`docker-compose.yml`](file:///c:/Users/RYZEN/OneDrive/Universidad/tercer%20a%C3%B1o/Backend/tpi-backend/docker-compose.yml).

### Ejecución en conjunto:
```powershell
# Desde la raíz: los Dockerfiles multi-stage compilan cada módulo dentro del contenedor
docker compose up --build -d
```
En Docker, el Gateway es el **único** servicio de negocio con puerto publicado (`8080`): los microservicios
quedan dentro de la red `tpi-network` (trusted zone).
