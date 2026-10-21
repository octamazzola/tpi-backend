# Definición del problema — Sistema de excursiones (Dominio 5)

> Documento de trabajo (no es entregable). Se actualiza a medida que el diseño evoluciona.
> Los puntos marcados con ⚠️ son supuestos provisorios o decisiones pendientes.

## 1. Contexto
Plataforma que administra actividades turísticas y excursiones ofrecidas por distintos
prestadores. Los turistas reservan lugares en salidas concretas. Una reserva válida puede
dejar de ser viable por el pronóstico del clima, por falta de confirmación u otros inconvenientes.

## 2. Actores
| Actor | Rol Keycloak | Qué hace |
|---|---|---|
| Visitante | (sin login) | Consulta el catálogo de actividades y salidas (público) |
| Turista | `TURISTA` | Reserva, confirma y cancela sus reservas |
| Prestador | `PRESTADOR` | Gestiona sus actividades y salidas, registra el cobro en destino, es notificado de cancelaciones |
| Administrador | `ADMIN` | Gestiona prestadores y supervisa toda la plataforma |

## 3. Reglas de negocio
| ID | Regla |
|---|---|
| RN-01 | Si el pronóstico indica lluvia durante la excursión: no se aceptan nuevas reservas y se cancelan las salidas **del día** de actividades al aire libre. |
| RN-02 | Toda reserva cancelada (por cualquier motivo) se notifica al cliente y al prestador. |
| RN-03 | No se puede reservar una cantidad de lugares mayor al cupo disponible. |
| RN-04 | El pago se realiza en el lugar de la excursión. No hay seña ni reembolso. |
| RN-05 | Cada actividad puede tener edad mínima y/o máxima. |
| RN-06 | Para reservar hay que estar autenticado. El catálogo es público. |
| RN-07 | 48 hs antes de la salida se envía automáticamente un aviso al turista para que confirme el turno. |
| RN-08 | 24 hs antes de la salida, si el pronóstico de lluvia no cambia, se cancelan las reservas **del día siguiente** (actividades al aire libre). |
| RN-09 | Las reservas hechas con menos de 48 hs de anticipación se confirman automáticamente. |
| RN-10 | Si el turista no confirma dentro del plazo, la reserva se cancela y se libera el cupo. |

### Línea de tiempo de una salida
| Momento | Qué pasa |
|---|---|
| Al reservar (≥ 48 hs antes) | Reserva en `PENDIENTE`. Ocupa cupo. |
| Al reservar (< 48 hs antes) | Reserva en `CONFIRMADA` automáticamente (RN-09). |
| T−48 h | Se envía el aviso de confirmación (RN-07). Se abre el plazo para confirmar. |
| T−24 h | Vence el plazo de confirmación (RN-10) y se reevalúa el clima de las salidas del día siguiente (RN-08). |
| Día de la salida | Se reevalúa el clima de las salidas del día (RN-01). |

### Plazo de confirmación
El aviso sale 48 hs antes de la salida y el turista puede confirmar hasta 24 hs antes (T−24 h).
Pasado ese momento, la reserva `PENDIENTE` se cancela y se libera el cupo. El valor es configurable.

### Precisiones que surgen de las reglas
- **RN-01 / RN-08:** cada actividad necesita un tipo (`AIRE_LIBRE` / `BAJO_TECHO`). Solo las `AIRE_LIBRE` se cancelan por lluvia.
- **RN-03:** el cupo se cuenta por *salida*, no por actividad.
- **RN-04:** la reserva tiene un estado de cobro (`PENDIENTE_EN_DESTINO` → `COBRADO`), que registra el prestador.
- **RN-05:** la edad se valida por *participante*, no por quien hace la reserva.
- **Criterio de lluvia (RN-01 / RN-08):** se consulta a Open-Meteo el pronóstico por hora para el horario de la excursión (de inicio a fin). Hay lluvia si, en alguna de esas horas, la probabilidad de precipitación es **≥ 60 %** o la precipitación esperada es **≥ 1 mm**. Ambos valores son configurables por actividad (campo "umbral de lluvia" en `ms-catalogo`) y los de arriba son los valores por defecto.

## 4. Responsabilidades por microservicio

### ms-catalogo — "qué se ofrece"
- Prestadores, actividades (tipo, dirección y coordenadas, edad mín/máx, precio, umbral de lluvia).
- Salidas: fecha/hora, cupo total, estado (`PROGRAMADA`, `CANCELADA`, `REALIZADA`).
- Es dueño de la capacidad y del estado de la salida.
- Expone la consulta pública (catálogo).
- Usa Google Maps: Geocoding (dirección → coordenadas al crear la actividad), búsqueda de excursiones cercanas a una ubicación y Directions (cómo llegar a la excursión).

### ms-reservas — "quién reservó y en qué estado"
- Reservas y participantes (nombre, edad).
- Estados: `PENDIENTE` → `CONFIRMADA` → `REALIZADA`, o `CANCELADA` desde cualquier estado previo.
- Es dueño del cupo *ocupado* (cupo disponible = cupo total del catálogo − lugares reservados).
- Consulta el clima (Open-Meteo), valida reglas, notifica y ejecuta los procesos programados.

### Componente común (JAR Maven)
DTOs compartidos, formato estándar de errores, utilidades de seguridad JWT/roles.
Lo que **no** se comparte: entidades JPA y lógica de negocio de cada servicio.

## 5. Procesos principales

### P1 — Crear reserva (colaboración ms-reservas ↔ ms-catalogo)
1. Turista autenticado → Gateway (valida JWT) → ms-reservas.
2. ms-reservas pide a ms-catalogo la salida y la actividad (REST).
3. Valida: salida `PROGRAMADA` y futura, edades de los participantes (RN-05).
4. Si la actividad es `AIRE_LIBRE`: consulta Open-Meteo para las coordenadas y la fecha; si hay lluvia, rechaza (RN-01).
5. Valida cupo (RN-03) con bloqueo para evitar sobreventa concurrente.
6. Crea la reserva: `PENDIENTE`, o `CONFIRMADA` si faltan menos de 48 hs (RN-09).

### P2 — Aviso de confirmación (job programado)
A T−48 h envía el aviso (RN-07). El turista confirma y la reserva pasa a `CONFIRMADA`.

### P3 — Vencimiento sin confirmar (job programado)
Vencido el plazo, las reservas `PENDIENTE` pasan a `CANCELADA`, se libera el cupo (RN-10) y se notifica (RN-02).

### P4 — Reevaluación por clima (job programado)
- **Día de la salida (RN-01):** si hay lluvia, cancela las reservas de las salidas `AIRE_LIBRE` del día y le pide a ms-catalogo marcar la salida `CANCELADA`.
- **T−24 h (RN-08):** misma lógica para las salidas del día siguiente, si el pronóstico no cambió.
- Notifica a cada cliente y al prestador (RN-02).

### P5 — Cancelación manual
Por el turista (su reserva) o por el prestador (la salida completa). Siempre notifica (RN-02).

### P6 — Cobro en destino
El prestador registra el cobro y/o asistencia; la reserva pasa a `REALIZADA`.

## 6. Sistemas externos
| Sistema | Uso | Estado |
|---|---|---|
| Keycloak | Identidad, OIDC, JWT, roles | Definido |
| Open-Meteo | Pronóstico para la viabilidad de la salida (RN-01, RN-08) | Definido |
| Google Maps (Geocoding) | Convierte la dirección de la actividad en coordenadas, que usa Open-Meteo. Permite buscar excursiones cercanas. | Definido |
| Google Maps (Directions) | Ruta y tiempo estimado para llegar a la excursión desde la ubicación del usuario. | Definido |
| Servidor SMTP | Envío de mails (RN-02, RN-07). Sin APIs de correo/SMS de terceros. En desarrollo: Mailpit. | Definido |

> Google Maps requiere API key con cuenta de facturación (hay crédito mensual gratuito).

## 7. Situaciones excepcionales (a decidir)
| Falla | Opción propuesta |
|---|---|
| Open-Meteo no responde al reservar | Permitir la reserva marcándola "clima no verificado" y reevaluarla en los jobs. Alternativa: rechazarla. |
| Pronóstico fuera de rango (Open-Meteo cubre ~16 días) | No se evalúa; se evalúa más cerca de la fecha. |
| ms-catalogo no responde | 503 al crear la reserva (sin datos no se puede validar). |
| Google Maps no responde | Permitir cargar las coordenadas manualmente. |
| Falla la notificación | La cancelación no se revierte; se reintenta y se registra el error. |

## 8. Decisiones tomadas
- Dos microservicios: `ms-catalogo` y `ms-reservas`. No se agregan más.
- Catálogo público; reservar requiere login.
- Sin seña ni reembolso.
- Reservas con menos de 48 hs se confirman solas.
- Reserva sin confirmar se cancela y libera el cupo.
- Docker queda para más adelante (se hará en Linux). Mientras tanto, desarrollo sin contenedores.
- Plazo de confirmación: hasta 24 hs antes de la salida.
- Notificaciones por SMTP.
- Google Maps: Geocoding + Directions.

## 9. Pendientes
1. ⚠️ Rehacer el C2 en una herramienta editable (el generado tiene conexiones incorrectas en SMTP y JDBC).
2. ⚠️ Definir la trusted zone: si los microservicios también validan el JWT.
3. ⚠️ Qué ubicación envía el usuario en "cómo llegar" y qué hacer si Google Directions falla.
