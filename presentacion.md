Acá tenés un guion claro y ordenado punto por punto para que mañana abras la reunión, les des el pantallazo completo y demuestres que el proyecto ya tiene bases técnicas sólidas:

---

### 🗣️ Guion para la reunión de equipo

#### 1. El Dominio y el Problema (¿Qué vamos a construir?)
> *"Elegimos el **Dominio 5: Gestión de actividades turísticas y excursiones**. El sistema permite a prestadores ofrecer excursiones y a turistas reservar lugares. El corazón del negocio no es un simple CRUD, sino las **reglas de viabilidad**:"*
* **Regla del clima:** Si hay pronóstico de lluvia, se cancelan las actividades al aire libre del día y no se aceptan nuevas reservas.
* **Confirmación a 48 hs:** Se envía un mail automático al turista 48 hs antes para que confirme su turno.
* **Vencimiento a 24 hs:** Si 24 hs antes el turista no confirmó, la reserva se cancela y se libera el cupo. Si el pronóstico de lluvia sigue firme para el día siguiente, también se cancela.
* **Pago en destino:** La reserva se paga en el lugar (no manejamos pasarelas de pago, lo que simplifica cancelaciones sin reembolsos).

---

#### 2. La Arquitectura y las Integraciones
> *"Cumpliendo con las restricciones del enunciado, definimos esta arquitectura:"*
* **2 Microservicios funcionales:**
  * **`ms-catalogo`:** Es el dueño de la oferta (prestadores, actividades, salidas, cupo total y umbrales de lluvia).
  * **`ms-reservas`:** Es el dueño de la demanda (reservas, participantes, estados y cálculo de cupo ocupado).
  * *Decisión clave:* La comunicación es en un solo sentido: **Reservas consulta a Catálogo**, el catálogo nunca llama a reservas.
* **API Gateway (`api-gateway`):** Punto único de entrada para los clientes.
* **JAR Común (`common-lib`):** Requisito obligatorio del TP. Es una librería propia donde compartimos DTOs (`SalidaDTO`), excepciones de negocio y el formato estándar de errores para no duplicar código.
* **Integraciones Externas:**
  1. **Open-Meteo:** API pública gratuita para consultar probabilidad y milímetros de lluvia.
  2. **OSRM (Open Source Routing Machine):** Para calcular distancias y tiempos de viaje hacia la excursión. Corre en un contenedor Docker local con el mapa de Argentina (descartamos Google Maps para evitar costos y tarjetas).
  3. **SMTP (Mailpit):** Servidor de correos para notificar cancelaciones y avisos de confirmación.
* **Seguridad (Keycloak):** Autenticación con OIDC/JWT y roles (`TURISTA`, `PRESTADOR`, `ADMIN`). Adoptamos la **Trusted Zone Opción B**: el Gateway valida en la entrada y cada microservicio revalida el token.

---

#### 3. Lo que YA está hecho y funcionando (Fase 0 terminada)
> *"Para no arrancar de cero, ya dejé armada y validada toda la estructura base del código y del entorno:"*
1. **Proyecto Maven Multi-Módulo:** En la raíz hay un `pom.xml` padre que centraliza versiones (Java 21 y Spring Boot 3.3.5) y administra los 4 módulos: `common-lib`, `ms-catalogo`, `ms-reservas` y `api-gateway`.
2. **Compilación exitosa:** Corrí `mvn clean compile` y los 4 módulos compilaron juntos con **BUILD SUCCESS** sin errores de dependencias.
3. **Entorno Docker listo:** Ya está configurado WSL2 con Docker Engine nativo en Ubuntu para correr la base de datos PostgreSQL, Mailpit y OSRM sin sobrecargar la RAM.
4. **Documentación inicial:** Ya redactamos la definición formal del problema y el diseño de endpoints/tablas en la carpeta `docs/`.

---

#### 4. Propuesta para hoy: Repartir el trabajo
> *"La base ya compila y no hay bloqueos. La idea de hoy es dividirnos los módulos para trabajar en paralelo:"*
* Uno toma **`ms-catalogo` + integración con OSRM**.
* Otro toma **`ms-reservas` + integración con Open-Meteo + Mails/Jobs**.
* Otro toma **`common-lib` (manejo de errores del Apunte 17) + `api-gateway` + Docker/Keycloak**.

---

Con este resumen el equipo va a tener una visión 100% clara de hacia dónde va el proyecto, qué decisiones ya están tomadas y cómo arrancar a codear de inmediato.