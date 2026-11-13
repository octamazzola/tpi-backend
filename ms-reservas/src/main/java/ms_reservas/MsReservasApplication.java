package ms_reservas;

import ar.edu.utn.frc.bda.common.exceptions.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Import(GlobalExceptionHandler.class): registra el manejador centralizado de errores de common-lib.
 * @EnableAsync: los emails se envían en segundo plano para no demorar la respuesta HTTP.
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@EnableAsync
public class MsReservasApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsReservasApplication.class, args);
	}

}
