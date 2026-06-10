package org.example.aiscanner_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiScannerServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiScannerServerApplication.class, args);
    }
}
