# 🎫 Microservicio de Reservas (`ms-reservas`)
**Backend de Aplicaciones — UTN FRC (2026)**  
**Dominio:** Sistema de Gestión de Actividades Turísticas y Excursiones  
**Arquitectura adoptada:** Opción A (Orquestador Central de Negocio)

---

## 🎯 1. Responsabilidad y Rol en el Sistema

`ms-reservas` es el **orquestador central** del negocio. Su objetivo es gestionar el ciclo de vida de las reservas creadas por los turistas, coordinando la validación inter-servicio y ejecutando las reglas clave de negocio:

* **Puerto local asignado:** `8082`
* **Prefijo en API Gateway:** `/api/v1/reservas`
* **Base de datos local:** H2 en memoria (`reservasdb`) / PostgreSQL

---

## 🔄 2. Flujo de Colaboración Inter-Servicio

Cuando un turista solicita una reserva (`POST /api/v1/reservas`), `ms-reservas` ejecuta el siguiente flujo:

```
                      [Turista / Cliente]
                               │
                               ▼ POST /api/v1/reservas
                     ┌───────────────────┐
                     │    ms-reservas    │ (Servicio Orquestador)
                     └─┬───────┬───────┬─┘
                       │       │       │
       1. Consulta     │       │       │ 3. Valida clima
          actividad    │       │       │    (si requiereBuenClima)
                       ▼       │       ▼
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

---

## 🔗 3. Integraciones que debe realizar `ms-reservas`

### A. Comunicación con `ms-catalogo` (Puerto `8081`)
* **Endpoint a consultar:** `GET http://localhost:8081/api/v1/catalogo/actividades/{id}`
* **Información que devuelve `ms-catalogo`:**
  ```json
  {
    "id": 12,
    "titulo": "Trekking al Champaquí",
    "precioBase": 45000.0,
    "cupoMaximo": 15,
    "latitudInicio": -31.9868,
    "longitudInicio": -64.9351,
    "direccionPuntoEncuentro": "Villa Alpina, Córdoba",
    "requiereBuenClima": true,
    "activa": true
  }
  ```
* **Reglas a validar:**
  1. Si `activa == false` o devuelve 404 $\rightarrow$ rechazar reserva (actividad no disponible).
  2. Guardar `precioBase` para calcular el `montoTotal = precioBase * cantidadPersonas`.
  3. Tomar `latitudInicio` y `longitudInicio` para enviárselas a `geoapi`.
  4. Leer `requiereBuenClima` para saber si consultar la API meteorológica.

---

### B. Comunicación con `geoapi` / OSRM (Puerto `8083`)
* **Endpoint a consultar:** `POST http://localhost:8083/api/v1/geo/rutas/distancia`
* **Payload que envía `ms-reservas`:**
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
* **Respuesta que recibe de `geoapi`:**
  ```json
  {
    "distanciaKm": 118.4,
    "duracionFormateada": "2 hs 15 min"
  }
  ```
* **Uso:** Almacenar la distancia y duración en la reserva para el itinerario del turista.

---

### C. Integración con API Pública Externa: Open-Meteo
*(Requisito obligatorio de la cátedra: Integración con API pública de valor real).*

* **Condición:** Solo si la actividad tiene `requiereBuenClima == true`.
* **URL a consumir:**
  ```
  GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&daily=precipitation_probability_max,temperature_2m_max&timezone=auto
  ```
* **Regla de Negocio:**
  * Si para la `fechaExcursion` la probabilidad de lluvia es superior al **70%** (o condición de tormenta severa), la reserva se crea en estado `RECHAZADA_POR_CLIMA` o `PENDIENTE_CONFIRMACION`.
* **Resiliencia (Fallback):**
  * Si Open-Meteo no responde o da timeout, la reserva se procesa normalmente con una advertencia en el response: `"alertaClimatica": false, "aviso": "Clima no disponible temporalmente"`.

---

## 🗄️ 4. Modelo de Datos sugerido (`ms_reservas.entities`)

### 4.1. Enum: `EstadoReserva.java`
```java
public enum EstadoReserva {
    PENDIENTE,
    CONFIRMADA,
    CANCELADA,
    RECHAZADA_POR_CLIMA
}
```

### 4.2. Entidad: `Reserva.java` (`@Table(name = "reservas")`)
```java
@Entity
@Table(name = "reservas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String turistaId; // Extraído del token JWT de Keycloak o DTO

    @Column(nullable = false)
    private Long actividadId; // Referencia lógica a ms-catalogo

    @Column(nullable = false)
    private LocalDate fechaExcursion;

    @Column(nullable = false)
    private Integer cantidadPersonas;

    @Column(nullable = false)
    private Double montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoReserva estado;

    // Datos calculados mediante OSRM / geoapi
    private Double distanciaKm;
    private String duracionEstimadaViaje;

    // Datos meteorológicos de Open-Meteo
    private Boolean alertaClimatica;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;
}
```

---

## 🌐 5. Endpoints a Exponer en `ms-reservas`

| Método | Endpoint | Rol Mínimo | Descripción |
|---|---|---|---|
| `POST` | `/api/v1/reservas` | `TURISTA` | Crea una reserva ejecutando la orquestación (Catálogo + OSRM + Clima). |
| `GET` | `/api/v1/reservas/mis-reservas` | `TURISTA` | Lista las reservas del usuario autenticado. |
| `GET` | `/api/v1/reservas/{id}` | `TURISTA`, `OPERADOR` | Detalle completo de una reserva. |
| `GET` | `/api/v1/reservas/actividad/{actividadId}` | `OPERADOR`, `ADMIN` | Lista todas las reservas de una excursión. |
| `PUT` | `/api/v1/reservas/{id}/confirmar` | `OPERADOR`, `ADMIN` | Confirma la reserva. |
| `PUT` | `/api/v1/reservas/{id}/cancelar` | `TURISTA`, `ADMIN` | Cancela la reserva. |

---

## 🛠️ 6. Recomendaciones Técnicas de Implementación

1. **Cliente HTTP:** Utilizar **`RestClient`** de Spring 6 / Spring Boot 3 para invocar a `ms-catalogo`, `geoapi` y Open-Meteo (es más moderno y legible que `RestTemplate`).
2. **Propiedades en `application.properties` (Puerto 8082):**
   ```properties
   server.port=8082
   spring.application.name=ms-reservas
   spring.datasource.url=jdbc:h2:mem:reservasdb
   spring.jpa.hibernate.ddl-auto=update
   spring.h2.console.enabled=true
   springdoc.swagger-ui.path=/swagger-ui.html

   # URLs de los servicios colaboradores
   servicios.catalogo.url=http://localhost:8081/api/v1/catalogo
   servicios.geoapi.url=http://localhost:8083/api/v1/geo
   servicios.openmeteo.url=https://api.open-meteo.com/v1/forecast

   # Servidor de correos Mailpit (SMTP local para envío de notificaciones simuladas)
   # En Docker Compose se inyecta SPRING_MAIL_HOST=mailpit
   spring.mail.host=${SPRING_MAIL_HOST:localhost}
   spring.mail.port=${SPRING_MAIL_PORT:1025}
   ```
3. **Manejo de Errores:** Apoyarse en `common-lib` para devolver errores unificados `ErrorResponseDTO`.

