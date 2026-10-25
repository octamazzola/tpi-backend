El cambio de Google Maps a **OSRM** es una decisión excelente:
- Está documentado en el apunte de la cátedra (`OSRM_routing_arg.pdf`).
- Corre en un contenedor Docker local (gratis, sin tarjetas ni límites de peticiones).
- Devuelve distancia en metros y tiempo en segundos para cualquier punto de Argentina.

Para la reunión de mañana, te preparé una **propuesta de división de tareas** pensada para que nadie se quede esperando al otro y puedan trabajar en paralelo.

---

### Opción A: Equipo de 3 personas (Esquema recomendado)

#### 👤 Integrante 1: Núcleo de Catálogo + Integración OSRM
* **Módulo principal:** `ms-catalogo`
* **Tareas:**
  1. Modelo de datos y persistencia: Entidades `Prestador`, `Actividad` y `Salida` con Spring Data JPA.
  2. Lógica y reglas: CRUD de actividades y salidas, control de vigencia y fechas.
  3. **Integración Externa OSRM:** Crear el cliente `RestClient` que consulta al contenedor de OSRM (`http://localhost:5000`) para calcular distancias y tiempos de llegada.
  4. Endpoints REST documentados con Swagger/OpenAPI.

#### 👤 Integrante 2: Núcleo de Reservas + Clima (Open-Meteo) + Mails
* **Módulo principal:** `ms-reservas`
* **Tareas:**
  1. Modelo de datos y persistencia: Entidades `Reserva` y `Participante`.
  2. Lógica de negocio y cupos: Validar edades de participantes y cupo disponible antes de reservar.
  3. **Colaboración REST:** Cliente `RestClient` para consultar salidas a `ms-catalogo`.
  4. **Integración Externa Clima:** Cliente `RestClient` para consultar el pronóstico en Open-Meteo.
  5. **Mails y Jobs programados:** Configurar `JavaMailSender` y los `@Scheduled` (aviso a las 48 hs, vencimiento a las 24 hs y cancelaciones por lluvia).

#### 👤 Integrante 3: Componente Común, Gateway, Seguridad e Infraestructura
* **Módulos principales:** `common-lib`, `api-gateway`, Docker y Keycloak
* **Tareas:**
  1. **`common-lib`:** Implementar el manejo global de errores (`@RestControllerAdvice`, `ErrorDTO` y excepciones) según el Apunte 17, para que los otros dos lo usen.
  2. **`api-gateway`:** Configurar las rutas de Spring Cloud Gateway hacia catálogo y reservas.
  3. **Seguridad / Keycloak:** Configurar el Realm, clientes y roles (`TURISTA`, `PRESTADOR`, `ADMIN`, `SERVICIO`), y el filtro de seguridad.
  4. **Docker & OSRM:** Preparar el `docker-compose.yml` base (PostgreSQL con los dos esquemas, Mailpit y el contenedor de OSRM con el mapa de Argentina).
  5. **Diagramas C4:** Pulir los diagramas C1 y C2 con draw.io para la entrega.

---

### Opción B: Si son 4 personas
Se divide el rol 3 en dos:
- **Integrante 3:** Se enfoca en **`common-lib`**, **`api-gateway`** y **Keycloak / Spring Security**.
- **Integrante 4:** Se enfoca en **Docker Compose** (Postgres, OSRM, Mailpit), **Diagramas C1/C2**, armar la colección de pruebas en **Postman** y documentar.

---

### 📅 Roadmap sugerido: ¿Cómo coordinar para no bloquearse?

1. **Paso 0 (Inmediato - 1 o 2 días):**
   * Quien tome `common-lib` sube el `ErrorDTO` y las excepciones compartidas (así los que hacen microservicios ya tienen dónde apoyarse).
   * En paralelo, levantar el contenedor de **OSRM** con Docker en WSL para tener disponible la API de rutas.
2. **Paso 1 (En paralelo - Trabajo grueso):**
   * El Integrante 1 desarrolla `ms-catalogo`.
   * El Integrante 2 desarrolla `ms-reservas` (inicialmente con H2 local y mockeando la llamada a catálogo si aún no está lista).
3. **Paso 2 (Integración):**
   * Se conectan `ms-reservas` con `ms-catalogo`.
   * Se prueba el flujo completo: reservar -> chequear clima -> calcular ruta con OSRM.
4. **Paso 3 (Cierre):**
   * Conectar el Gateway adelante y activar la seguridad con Keycloak.
   * Empaquetar todo en el `docker-compose.yml` final.

---

### 📝 Puntos clave a acordar en la reunión de mañana
1. **Validar la división:** Que cada uno elija el módulo donde se sienta más cómodo.
2. **Uso de Git:** Acordar trabajar con ramas (ej: `feature/catalogo`, `feature/reservas`, `feature/gateway`) y no pushear directo a `main`.
3. **OSRM:** Confirmar que van a correr OSRM en Docker con el archivo de Argentina (`argentina-latest.osm.pbf`) como indica el apunte.
4. **Diagramas C1 y C2:** Confirmar quién los pasa en limpio en draw.io teniendo en cuenta el cambio de Google Maps a OSRM.