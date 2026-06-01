package org.daneel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DaneelclawApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaneelclawApplication.class, args);
    }
}
