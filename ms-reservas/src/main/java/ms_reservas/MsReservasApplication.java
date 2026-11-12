package ms_reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableAsync: los emails se envían en segundo plano para no demorar la respuesta HTTP.
 * @EnableScheduling: habilita la revalidación periódica de reservas (ver RevalidacionScheduler).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MsReservasApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsReservasApplication.class, args);
	}

}
