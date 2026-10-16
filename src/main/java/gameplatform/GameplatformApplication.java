package gameplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GameplatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(GameplatformApplication.class, args);
	}

}
