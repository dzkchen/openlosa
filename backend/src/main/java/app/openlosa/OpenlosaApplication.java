package app.openlosa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenlosaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenlosaApplication.class, args);
    }
}
