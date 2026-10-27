# 🧭 Microservicio de Catálogo (`ms-catalogo`)
**Backend de Aplicaciones — UTN FRC (2026)**  
**Dominio:** Sistema de Gestión de Actividades Turísticas y Excursiones

---

## 📋 1. Descripción y Responsabilidad

El microservicio `ms-catalogo` es el componente central encargado de la administración del catálogo de excursiones y actividades turísticas ofrecidas por prestadores.

### Responsabilidades clave dentro del sistema:
* **Gestión de Categorías:** Clasificación de excursiones (Trekking, Navegación, Cabalgata, etc.).
* **Gestión de Actividades Turísticas:** Alta, edición, filtrado y baja lógica de actividades con sus características (dificultad, cupo máximo, precio base, coordenadas geográficas).
* **Integración inter-servicio:** 
  * Provee coordenadas geográficas (`latitudInicio`, `longitudInicio`) para el cálculo de distancias y traslados mediante `geoapi` (OSRM).
  * Informa la bandera `requiereBuenClima` para que `ms-reservas` valide la viabilidad meteorológica con **Open-Meteo**.
  * Expone la consulta de disponibilidad de cupos para la orquestación de reservas.

---

## 🏗️ 2. Arquitectura Interna en Capas

El microservicio implementa una arquitectura limpia y desacoplada organizada en los siguientes paquetes:

```
ms-catalogo/src/main/java/ms_catalogo/
├── entities/           # Entidades JPA (Modelo relacional)
├── repositories/       # Repositorios Spring Data JPA
├── dtos/               # Data Transfer Objects (Validación de entrada y contratos de salida)
├── mappers/            # Componentes de mapeo Entidad <-> DTO
├── exceptions/         # Excepciones de negocio HTTP
├── services/           # Interfaces de negocio (Patrón Separated Interface)
│   └── impl/           # Implementaciones con lógica de negocio y @Transactional
└── controllers/        # Controladores REST (@RestController) - Próxima fase
```

---

## 🧩 3. Componentes Implementados

### 3.1. Entidades y Enums (`entities`)
* **`Categoria` (`@Table(name = "categorias")`):** Identificador autoincremental, nombre obligatorio único y descripción.
* **`Dificultad` (`enum`):** Valores fijos `BAJA`, `MEDIA`, `ALTA` (almacenados como `STRING` en la base de datos).
* **`Actividad` (`@Table(name = "actividades")`):**
  * Relación `@ManyToOne` hacia `Categoria` (clave foránea `categoria_id`).
  * `@Enumerated(EnumType.STRING)` para `Dificultad`.
  * Punto geográfico de encuentro (`latitudInicio`, `longitudInicio`, `direccionPuntoEncuentro`).
  * Bandera `requiereBuenClima` (para integración con API climática).
  * Campo `activa` inicializado en `true` (soporta la baja lógica).

### 3.2. Repositorios (`repositories`)
* **`CategoriaRepository` (`JpaRepository<Categoria, Long>`):**
  * `existsByNombreIgnoreCase(String nombre)`: Valida nombres duplicados antes de insertar.
  * `findByNombreIgnoreCase(String nombre)`: Búsqueda exacta case-insensitive.
* **`ActividadRepository` (`JpaRepository<Actividad, Long>`):**
  * `findByActivaTrue()`: Retorna todas las excursiones activas (oculta bajas lógicas).
  * `findByIdAndActivaTrue(Long id)`: Consulta individual respetando vigencia.
  * `findByCategoriaIdAndActivaTrue(Long categoriaId)`: Filtrado por categoría.
  * `findByDificultadAndActivaTrue(Dificultad dificultad)`: Filtrado por dificultad.

### 3.3. DTOs y Validación (`dtos`)
* **`CategoriaRequestDTO` / `CategoriaResponseDTO`:** Creación y respuesta limpia de categorías sin exponer la entidad.
* **`ActividadRequestDTO`:** Contrato de entrada para `POST` y `PUT` con Bean Validation:
  * `@NotBlank` y `@Size(max = 150)` en `titulo`.
  * `@Positive` en `precioBase`.
  * `@Min(1)` en `cupoMaximo`.
  * `@NotNull` en categoría, dificultad y coordenadas.
* **`ActividadPatchDTO`:** DTO para modificaciones parciales (`PATCH`) donde todos los atributos son opcionales y solo se aplican los campos no nulos recibidos.
* **`ActividadResponseDTO`:** Respuesta detallada con la categoría anidada (`CategoriaResponseDTO`) y el estado de la actividad.
* **`DisponibilidadDTO`:** DTO liviano para responder estado de cupos a `ms-reservas`.
* **`ErrorResponseDTO`:** Formato estructurado y estandarizado para respuestas de error de la API (conforme a los lineamientos de cátedra).

### 3.4. Mappers (`mappers`)
* **`CategoriaMapper`:** Transforma `CategoriaRequestDTO` a entidad y entidad a `CategoriaResponseDTO`.
* **`ActividadMapper`:** Realiza el ensamblado de `Actividad` vinculando la entidad `Categoria`, y provee el método `updateEntityFromDTO` para actualizaciones limpias.

### 3.5. Capa de Servicios (`services` & `services/impl`)
Implementa el patrón **Separated Interface** (SOLID - Inversión de Dependencias):
* **`CategoriaService` / `CategoriaServiceImpl`:**
  * `obtenerTodas()` (`@Transactional(readOnly = true)`)
  * `obtenerPorId(Long id)` con validación de existencia (`ResourceNotFoundException`).
  * `crear(CategoriaRequestDTO dto)` con verificación de nombres duplicados (`BadRequestException`).
  * `eliminar(Long id)`: Elimina la categoría **únicamente si no posee actividades asociadas** (garantía de integridad referencial).
* **`ActividadService` / `ActividadServiceImpl`:**
  * `listarActividades(...)`: Permite filtrar opcionalmente por categoría, dificultad o precio máximo sobre actividades activas.
  * `obtenerPorId(Long id)`: Busca la actividad o lanza 404 si fue dada de baja.
  * `consultarDisponibilidad(Long id)`: Retorna si hay cupos disponibles.
  * `crear(ActividadRequestDTO dto)`: Valida la existencia previa de la categoría y persiste la excursión.
  * `actualizar(Long id, ActividadRequestDTO dto)`: Reemplazo total de atributos (`PUT`).
  * `actualizarParcial(Long id, ActividadPatchDTO dto)`: Modificación puntual de atributos seleccionados (`PATCH`).
  * `darDeBaja(Long id)`: **Baja lógica** (`activa = false`) para preservar integridad histórica con las reservas existentes (`DELETE`).

### 3.6. Controladores REST (`controllers`)
* **`CategoriaController` (`/api/v1/catalogo/categorias`):**
  * `GET /`: Listar todas las categorías.
  * `GET /{id}`: Obtener categoría por ID.
  * `POST /`: Crear nueva categoría (`@Valid`).
  * `DELETE /{id}`: Eliminar categoría sin excursiones dependientes.
* **`ActividadController` (`/api/v1/catalogo/actividades`):**
  * `GET /`: Listar actividades con filtros (`categoriaId`, `dificultad`, `precioMax`).
  * `GET /{id}`: Obtener detalle completo por ID.
  * `GET /{id}/disponibilidad`: Consulta inter-servicio de cupos para `ms-reservas`.
  * `POST /`: Crear actividad (`@Valid`).
  * `PUT /{id}`: Actualización completa (`@Valid`).
  * `PATCH /{id}`: Actualización parcial de campos (`@Valid`).
  * `DELETE /{id}`: Baja lógica de la actividad.

### 3.7. Gestión Global de Errores (`exceptions`)
* **`GlobalExceptionHandler` (`@RestControllerAdvice`):**
  * Intercepta `ResourceNotFoundException` devolviendo `404 Not Found`.
  * Intercepta `BadRequestException` devolviendo `400 Bad Request`.
  * Intercepta errores de Bean Validation (`MethodArgumentNotValidException`) devolviendo `400 Bad Request` con el detalle de campos inválidos.
  * Intercepta errores genéricos no controlados devolviendo `500 Internal Server Error`.

---

## 💡 4. Patrones de Diseño y Principios Arquitectónicos Implementados

El microservicio aplica formalmente un conjunto de patrones de diseño clásicos (GoF, Martin Fowler y DDD) y principios de ingeniería de software:

| Patrón / Principio | Tipo / Autor | Dónde se aplica en `ms-catalogo` | Beneficio técnico |
|---|---|---|---|
| **Layered Architecture** *(Arquitectura en Capas)* | Arquitectónico | División estricta en `controllers`, `services`, `mappers`, `repositories`, `entities`. | Alta cohesión, bajo acoplamiento y separación de incumbencias (SoC). |
| **Data Transfer Object (DTO)** | Fowler (PoEAA) | Paquete `dtos/` (`ActividadRequestDTO`, `ActividadPatchDTO`, `ErrorResponseDTO`, etc.). | Desacopla la API del esquema SQL, previene *over-posting* (seguridad) y aplica Bean Validation. |
| **Repository Pattern** | Eric Evans (DDD) / Spring Data | `CategoriaRepository`, `ActividadRepository` (`JpaRepository`). | Aísla la capa de negocio del acceso a datos, simulando una colección de objetos en memoria. |
| **Service Layer** *(Capa de Servicio)* | Fowler (PoEAA) | `CategoriaService`, `ActividadService` con `@Transactional`. | Centraliza las reglas de negocio, coordinando repositorios y mappers en transacciones atómicas. |
| **Separated Interface** | Fowler (PoEAA) | Interfaces en `services/` e implementaciones en `services/impl/`. | Cumple con la **D** de SOLID (Inversión de Dependencias). Los controladores solo conocen la interfaz, facilitando mocks para tests. |
| **Data Mapper / Mapper** | Fowler (PoEAA) | `CategoriaMapper`, `ActividadMapper` (`@Component`). | Traduce datos entre Entidades y DTOs sin ensuciar la lógica de negocio con código repetitivo de conversión. |
| **Builder Pattern** | Gang of Four (GoF) / Lombok | `@Builder` en Entidades y DTOs (`Actividad.builder()...build()`). | Construcción fluida, legible e inmutable de objetos, evitando constructores sobrecargados (*telescoping constructor*). |
| **Dependency Injection (DI / IoC)** | Principio SOLID / Spring | Inyección por constructor mediante `@RequiredArgsConstructor` y campos `final`. | Inmutabilidad de dependencias, desacoplamiento y facilidad para pruebas unitarias. |
| **Soft Delete** *(Baja Lógica)* | Patrón de Datos | Campo `activa = false` en `Actividad` y métodos `findByActivaTrue()`. | Preserva la integridad referencial histórica con futuras reservas sin destruir registros físicos. |
| **Exception Shielding / Global Handling** | Patrón Empresarial | `@RestControllerAdvice` con `GlobalExceptionHandler`. | Centraliza el manejo de excepciones, oculta detalles técnicos internos y unifica las respuestas HTTP. |

---

## 🛡️ 5. Los 3 Niveles de Validación

| Nivel | Componente | Responsabilidad | Ejemplo en el código |
|---|---|---|---|
| **1. Sintáctico / Formato** | DTOs (Bean Validation) | Valida que los datos de entrada sean correctos antes de entrar a la lógica. | `@NotBlank`, `@Positive`, `@Min(1)`, `@Size` |
| **2. Semántico / Negocio** | Services (`services/impl`) | Valida existencia de relaciones, reglas de negocio y duplicados. | `existsByNombreIgnoreCase`, `existsByCategoriaId`, `findByIdAndActivaTrue` |
| **3. Integridad SQL** | Entidades JPA | Garantiza restricciones relacionales en el motor de base de datos. | `@Column(nullable = false)`, `@JoinColumn` |

---

## ⚙️ 6. Configuración y Ejecución

* **Puerto:** `8081` (acceso directo) / `/api/v1/catalogo` (a través del API Gateway en el puerto `8080`).
* **Base de datos local:** H2 en memoria (`catalogodb`).
  * **Consola H2 Web:** `http://localhost:8081/h2-console`
  * **JDBC URL:** `jdbc:h2:mem:catalogodb` | **User:** `sa` | **Password:** *(vacío)*
* **Documentación OpenAPI / Swagger:** `http://localhost:8081/swagger-ui.html`

### Compilación y verificación:
```powershell
mvn clean compile -pl ms-catalogo
```

---

## 📌 7. Próximos Pasos en el Microservicio
1. **Datos de prueba (`DataLoader`):** Sembrar actividades y categorías reales de Córdoba para pruebas rápidas.
2. **Seguridad:** Configurar validación de tokens JWT emitidos por Keycloak y protección por roles (`ADMIN`, `OPERADOR`, `TURISTA`).
3. **Contenerización:** Construir el `Dockerfile` de `ms-catalogo` para integrarlo al `docker-compose.yml`.
