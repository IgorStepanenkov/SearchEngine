package searchengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Application {
    public static volatile boolean isIndexingInProcess = false;
    public static volatile boolean cancelIndexingProcess = false;
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
