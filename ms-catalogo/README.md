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
* **`ActividadResponseDTO`:** Respuesta detallada con la categoría anidada (`CategoriaResponseDTO`) y el estado de la actividad.
* **`DisponibilidadDTO`:** DTO liviano para responder estado de cupos a `ms-reservas`.

### 3.4. Mappers (`mappers`)
* **`CategoriaMapper`:** Transforma `CategoriaRequestDTO` a entidad y entidad a `CategoriaResponseDTO`.
* **`ActividadMapper`:** Realiza el ensamblado de `Actividad` vinculando la entidad `Categoria`, y provee el método `updateEntityFromDTO` para actualizaciones limpias.

### 3.5. Capa de Servicios (`services` & `services/impl`)
Implementa el patrón **Separated Interface** (SOLID - Inversión de Dependencias):
* **`CategoriaService` / `CategoriaServiceImpl`:**
  * `obtenerTodas()` (`@Transactional(readOnly = true)`)
  * `obtenerPorId(Long id)` con validación de existencia (`ResourceNotFoundException`).
  * `crear(CategoriaRequestDTO dto)` con verificación de nombres duplicados (`BadRequestException`).
* **`ActividadService` / `ActividadServiceImpl`:**
  * `listarActividades(...)`: Permite filtrar opcionalmente por categoría, dificultad o precio máximo sobre actividades activas.
  * `obtenerPorId(Long id)`: Busca la actividad o lanza 404 si fue dada de baja.
  * `consultarDisponibilidad(Long id)`: Retorna si hay cupos disponibles.
  * `crear(ActividadRequestDTO dto)`: Valida la existencia previa de la categoría y persiste la excursión.
  * `actualizar(Long id, ActividadRequestDTO dto)`: Modifica los atributos de la actividad.
  * `darDeBaja(Long id)`: **Baja lógica** (`activa = false`) para preservar integridad histórica con las reservas existentes.

---

## 💡 4. Decisiones de Diseño y Justificaciones Técnicas

| Decisión Técnica | Justificación para el TPI |
|---|---|
| **Patrón Separated Interface (`impl/`)** | Cumple con el Principio de Inversión de Dependencias (SOLID). El controlador solo conoce la interfaz, permitiendo mockear servicios en tests unitarios. |
| **Separación en 3 Niveles de Validación** | *Nivel DTO:* Rechaza entradas con formato incorrecto (`400 Bad Request`) en la entrada HTTP.<br>*Nivel Service:* Valida existencia de entidades y reglas de negocio.<br>*Nivel Entidad:* Garantiza integridad referencial en la base de datos SQL. |
| **Baja Lógica (`activa = false`)** | Un `DELETE /actividades/{id}` físico rompería las reservas históricas o asociadas de los turistas. La baja lógica oculta la actividad del catálogo sin destruir datos. |
| **`@Enumerated(EnumType.STRING)`** | Evita inconsistencias en base de datos si en el futuro se agregan nuevos niveles de dificultad al enum. |

---

## ⚙️ 5. Configuración y Ejecución

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

## 📌 6. Próximos Pasos en el Microservicio
1. **Controladores REST:** Implementar `CategoriaController` y `ActividadController`.
2. **Seguridad:** Configurar validación de tokens JWT emitidos por Keycloak y protección por roles (`ADMIN`, `OPERADOR`, `TURISTA`).
3. **Contenerización:** Construir el `Dockerfile` de `ms-catalogo` para integrarlo al `docker-compose.yml`.
