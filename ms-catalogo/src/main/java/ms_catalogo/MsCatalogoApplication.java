package ms_catalogo;

import ar.edu.utn.frc.bda.common.exceptions.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class MsCatalogoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsCatalogoApplication.class, args);
	}

}
