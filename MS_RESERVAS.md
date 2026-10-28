# 🎫 Guía de Implementación: Microservicio de Reservas (`ms-reservas`)
**Backend de Aplicaciones — UTN FRC (2026)**  
**Arquitectura de Microservicios: Spring Boot 3.3.5 / Java 21 / Docker**

Este documento está diseñado para guiar paso a paso a los desarrolladores del equipo en la construcción de **`ms-reservas`**, siguiendo las mismas decisiones técnicas, estándares arquitectónicos y directrices de calidad que implementamos en `ms-catalogo`, `geoapi` y `api-gateway`.

---

## 🧠 1. Filosofía de Desarrollo del Equipo (Karpathy Guidelines)

* **Think Before Coding:** Comprender la interacción entre los 3 servicios antes de programar. No asumir requerimientos ni inventar configuraciones innecesarias.
* **Simplicity First:** Usar **`RestClient`** de Spring Boot 3 (sincrónico, limpio y legible) para consumir los otros microservicios. No usar WebFlux reactivo innecesariamente dentro de este servicio.
* **Surgical Precision:** Tocar únicamente los archivos y clases dentro de la carpeta `ms-reservas/`.
* **Goal-Driven Verification:** Antes de dar una tarea por lista, verificar con `mvn clean compile -pl ms-reservas` y probar que los endpoints respondan los códigos HTTP estándar (`200 OK`, `201 Created`, `400 Bad Request`, `404 Not Found`).

---

## 🎯 2. Rol y Responsabilidad de `ms-reservas`

`ms-reservas` es el **orquestador central** del negocio del TPI. Su responsabilidad es coordinar el ciclo de vida de una reserva turística colaborando con los demás servicios del ecosistema:

* **Puerto local asignado:** `8082`
* **Prefijo en API Gateway:** `/api/v1/reservas` (todas las peticiones del frontend o Postman entran por el Gateway en el puerto `8080`)
* **Base de datos local:** H2 en memoria (`reservasdb`) para desarrollo ágil / PostgreSQL para Docker
* **Servidor de correos:** Mailpit (`localhost:1025` SMTP / `http://localhost:8025` Web UI)

---

## 🔄 3. El Proceso Integrador: Flujo de `POST /api/v1/reservas`

Este es el proceso de negocio obligatorio exigido por la cátedra (*"al menos un proceso significativo que involucre más de un microservicio"*):

```
       [Cliente / Turista]
               │
               ▼  POST /api/v1/reservas
        [api-gateway] (Puerto 8080)
               │
               ▼
        [ms-reservas] (Puerto 8082 - Servicio Orquestador)
         ├── 1. Llama a [ms-catalogo] (8081): GET /api/v1/catalogo/actividades/{id}
         │      -> Valida que exista, esté activa y obtiene: precioBase, cupoMaximo, coords y requiereBuenClima.
         │
         ├── 2. Valida cupo:
         │      -> Suma las personas ya reservadas para esa fecha y valida que no supere el cupoMaximo.
         │
         ├── 3. Si requiereBuenClima == true -> Llama a [Open-Meteo API Externa]:
         │      -> GET https://api.open-meteo.com/v1/forecast?...
         │      -> Si lluvia > 70% o alerta meteorológica, crea la reserva en estado RECHAZADA_POR_CLIMA.
         │      -> Si la API de clima falla (timeout o caída), aplica FALLBACK (continúa con alertaClimatica = false).
         │
         ├── 4. Llama a [geoapi] (8083): POST /api/v1/geo/rutas/distancia
         │      -> Envía origen del turista y destino de la excursión.
         │      -> Obtiene distanciaKm y duracionFormateada ("X hs Y min").
         │
         ├── 5. Calcula montoTotal:
         │      -> montoTotal = precioBase * cantidadPersonas.
         │
         ├── 6. Guarda en Base de Datos:
         │      -> Registra la Reserva con estado CONFIRMADA (o RECHAZADA_POR_CLIMA).
         │
         ├── 7. Envía Notificación por Email (Mailpit):
         │      -> Envía correo simulado de confirmación con el itinerario al email del turista.
         │
         └── 8. Responde 201 Created con el DTO completo de la reserva.
```

---

## 📁 4. Estructura de Paquetes Recomendada

```text
ms-reservas/src/main/java/ms_reservas/
├── MsReservasApplication.java
├── config/
│   ├── RestClientConfig.java       # Bean central de RestClient para llamadas HTTP
│   └── DataLoader.java             # Precarga de reservas de prueba al iniciar (CommandLineRunner)
├── entities/
│   ├── Reserva.java                # @Entity JPA (@Table(name = "reservas"))
│   └── EstadoReserva.java          # Enum (PENDIENTE, CONFIRMADA, CANCELADA, RECHAZADA_POR_CLIMA)
├── repositories/
│   └── ReservaRepository.java      # JpaRepository con queries personalizadas
├── dtos/
│   ├── request/
│   │   ├── ReservaRequestDTO.java  # Validaciones con Jakarta Validation (@NotNull, @Min, etc.)
│   │   └── OrigenTuristaDTO.java   # Coordenadas y dirección del turista
│   ├── response/
│   │   ├── ReservaResponseDTO.java # Respuesta enriquecida para el cliente
│   │   └── TrasladoEstimadoDTO.java# Distancia y duración de viaje calculada por OSRM
│   └── external/                   # DTOs para mapear las respuestas de los otros servicios
│       ├── ActividadCatalogoDTO.java
│       ├── RutaGeoRequestDTO.java
│       ├── RutaGeoResponseDTO.java
│       └── OpenMeteoResponseDTO.java
├── clients/                        # Clientes HTTP hacia otros servicios (usando RestClient)
│   ├── CatalogoClient.java         # Llama a ms-catalogo (8081)
│   ├── GeoApiClient.java           # Llama a geoapi (8083)
│   └── OpenMeteoClient.java        # Llama a Open-Meteo con resiliencia/fallback
├── services/
│   ├── ReservaService.java         # Interfaz de negocio
│   ├── EmailService.java           # Envío de correos SMTP a Mailpit
│   └── impl/
│       ├── ReservaServiceImpl.java # Implementación del orquestador (@Transactional)
│       └── EmailServiceImpl.java   # Implementación del servicio de email con JavaMailSender
├── controllers/
│   └── ReservaController.java      # Endpoints REST (@RequestMapping("/api/v1/reservas"))
└── exceptions/
    ├── ResourceNotFoundException.java
    ├── BadRequestException.java
    └── GlobalExceptionHandler.java # @RestControllerAdvice devolviendo ErrorResponseDTO
```

---

## 🗄️ 5. Modelo de Datos y Entidades

### 5.1. Enum `EstadoReserva.java`
```java
package ms_reservas.entities;

public enum EstadoReserva {
    PENDIENTE,
    CONFIRMADA,
    CANCELADA,
    RECHAZADA_POR_CLIMA
}
```

### 5.2. Entidad `Reserva.java`
```java
package ms_reservas.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    // Identificador del turista (obtenido del token JWT o del request)
    @Column(nullable = false, length = 100)
    private String turistaId;

    @Column(nullable = false, length = 150)
    private String emailTurista;

    // Referencia lógica al microservicio de Catálogo
    @Column(nullable = false)
    private Long actividadId;

    @Column(nullable = false)
    private String tituloActividad;

    @Column(nullable = false)
    private LocalDate fechaExcursion;

    @Column(nullable = false)
    private Integer cantidadPersonas;

    @Column(nullable = false)
    private Double montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoReserva estado;

    // Datos calculados mediante geoapi / OSRM
    private Double distanciaKm;
    private String duracionEstimadaViaje;

    // Datos meteorológicos de Open-Meteo
    private Boolean alertaClimatica;
    private String descripcionClima;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;
}
```

---

## 📡 6. Contratos de los Servicios Colaboradores

### A. Consumo de `ms-catalogo` (Puerto `8081`)
* **Endpoint:** `GET http://localhost:8081/api/v1/catalogo/actividades/{id}`
* **DTO que debe mapear `ms-reservas`:**
```java
package ms_reservas.dtos.external;

public record ActividadCatalogoDTO(
    Long id,
    String titulo,
    Double precioBase,
    Integer cupoMaximo,
    Double latitudInicio,
    Double longitudInicio,
    String direccionPuntoEncuentro,
    Boolean requiereBuenClima,
    Boolean activa
) {}
```
* **Regla:** Si `activa == false` o la actividad devuelve 404, lanzar `ResourceNotFoundException("La actividad seleccionada no existe o no se encuentra disponible")`.

---

### B. Consumo de `geoapi` / OSRM (Puerto `8083`)
* **Endpoint:** `POST http://localhost:8083/api/v1/geo/rutas/distancia`
* **Request que envía `ms-reservas`:**
```java
package ms_reservas.dtos.external;

public record RutaGeoRequestDTO(
    double latitudOrigen,
    double longitudOrigen,
    double latitudDestino,
    double longitudDestino
) {}
```
* **Response que devuelve `geoapi`:**
```java
package ms_reservas.dtos.external;

public record RutaGeoResponseDTO(
    double distanciaKm,
    int duracionMinutos,
    String duracionFormateada // Ej: "2 hs 15 min"
) {}
```

---

### C. Consumo de `Open-Meteo API` (API Pública Externa)
* **URL:**
  `https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&daily=weather_code,precipitation_probability_max,temperature_2m_max&timezone=America/Argentina/Cordoba`
* **Regla de Clima:**
  * Si la actividad tiene `requiereBuenClima == true`:
    * Si para la `fechaExcursion`, `precipitation_probability_max > 70%` o `weather_code >= 61` (lluvia / tormenta):
      * La reserva se crea con `EstadoReserva.RECHAZADA_POR_CLIMA` y `alertaClimatica = true`.
* **Resiliencia (Fallback obligatorio):**
  * Si Open-Meteo falla por timeout o falta de internet, **no romper la reserva**: capturar la excepción con `try/catch`, setear `alertaClimatica = false`, loguear advertencia y continuar el flujo normal.

---

## ✉️ 7. Envío de Notificaciones por Email (Mailpit)

El proyecto incluye el contenedor `mailpit` en Docker (puerto SMTP `1025` y bandeja visual en `http://localhost:8025`).

Ejemplo de servicio de email con Spring Mail:
```java
package ms_reservas.services.impl;

import lombok.RequiredArgsConstructor;
import ms_reservas.entities.Reserva;
import ms_reservas.services.EmailService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void enviarConfirmacionReserva(Reserva reserva) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(reserva.getEmailTurista());
            message.setSubject("Confirmación de Reserva #" + reserva.getId() + " - " + reserva.getTituloActividad());
            message.setText("¡Hola! Tu reserva ha sido " + reserva.getEstado() + ".\n"
                    + "Fecha de la excursión: " + reserva.getFechaExcursion() + "\n"
                    + "Personas: " + reserva.getCantidadPersonas() + "\n"
                    + "Monto Total: $" + reserva.getMontoTotal() + "\n"
                    + "Tiempo de traslado estimado: " + reserva.getDuracionEstimadaViaje() + " (" + reserva.getDistanciaKm() + " km)\n");
            mailSender.send(message);
        } catch (Exception e) {
            // Loguear error pero nunca interrumpir la transacción de la reserva
        }
    }
}
```

---

## 🌐 8. Endpoints REST de `ReservaController`

Todos bajo el prefijo `/api/v1/reservas`:

| Método | Endpoint | Rol Mínimo | Descripción |
|---|---|---|---|
| `POST` | `/api/v1/reservas` | `TURISTA` | Crea una reserva ejecutando toda la orquestación. Devuelve `201 Created`. |
| `GET` | `/api/v1/reservas/mis-reservas` | `TURISTA` | Lista reservas del turista logueado (filtradas por `turistaId`). |
| `GET` | `/api/v1/reservas/{id}` | `TURISTA`, `OPERADOR` | Detalle completo de una reserva. |
| `GET` | `/api/v1/reservas/actividad/{actividadId}` | `OPERADOR`, `ADMIN` | Lista todas las reservas de una excursión. |
| `PUT` | `/api/v1/reservas/{id}/confirmar` | `OPERADOR`, `ADMIN` | Confirma una reserva pendiente. |
| `PUT` | `/api/v1/reservas/{id}/cancelar` | `TURISTA`, `ADMIN` | Cancela la reserva (`EstadoReserva.CANCELADA`). |

---

## ⚙️ 9. Configuración de `application.properties`

Colocar en `ms-reservas/src/main/resources/application.properties`:

```properties
server.port=8082
spring.application.name=ms-reservas

# Base de datos H2 en memoria para desarrollo local
spring.datasource.url=jdbc:h2:mem:reservasdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Swagger OpenAPI interactivo (/swagger-ui.html)
springdoc.swagger-ui.path=/swagger-ui.html

# URLs de los Microservicios Colaboradores
servicios.catalogo.url=${MS_CATALOGO_URL:http://localhost:8081/api/v1/catalogo}
servicios.geoapi.url=${GEOAPI_URL:http://localhost:8083/api/v1/geo}
servicios.openmeteo.url=https://api.open-meteo.com/v1/forecast

# Servidor de correos Mailpit (SMTP local)
spring.mail.host=${SPRING_MAIL_HOST:localhost}
spring.mail.port=${SPRING_MAIL_PORT:1025}
spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false
```

---

## 🐳 10. Dockerización de `ms-reservas`

### `ms-reservas/Dockerfile`:
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/ms-reservas-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Servicio a agregar en `docker-compose.yml`:
```yaml
  ms-reservas:
    container_name: ms-reservas
    build:
      context: ./ms-reservas
      dockerfile: Dockerfile
    ports:
      - "8082:8082"
    environment:
      SPRING_APPLICATION_NAME: ms-reservas
      SERVER_PORT: 8082
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-db:5432/reservasdb
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: org.postgresql.Driver
      SPRING_JPA_DATABASE_PLATFORM: org.hibernate.dialect.PostgreSQLDialect
      SPRING_JPA_HIBERNATE_DDL_AUTO: update
      SPRING_H2_CONSOLE_ENABLED: "false"
      MS_CATALOGO_URL: http://ms-catalogo:8081/api/v1/catalogo
      GEOAPI_URL: http://geoapi:8083/api/v1/geo
      SPRING_MAIL_HOST: mailpit
      SPRING_MAIL_PORT: 1025
    depends_on:
      postgres-db:
        condition: service_healthy
      ms-catalogo:
        condition: service_started
      mailpit:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - tpi-network
```

---

## 🧪 11. Pasos de Validación y Pruebas

1. **Compilar con Maven:**
   ```powershell
   mvn clean compile -pl ms-reservas
   ```
2. **Ejecutar localmente:**
   ```powershell
   mvn -pl ms-reservas spring-boot:run
   ```
3. **Swagger UI:** Entrar a `http://localhost:8082/swagger-ui.html`.
4. **Colección Postman:** Usar la colección de la raíz `tpi_postman_collection.json` en la carpeta **`2. Microservicio Reservas`**.
5. **Verificar correos en Mailpit:** Entrar a `http://localhost:8025` para corroborar la llegada de los correos de confirmación.
