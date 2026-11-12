package ms_reservas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj del sistema en la zona horaria de Córdoba. Se inyecta en lugar de llamar a LocalDate.now()
 * para que las reglas que dependen de la fecha (48 hs, pronóstico) se puedan testear con un reloj fijo.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Argentina/Cordoba"));
    }
}
