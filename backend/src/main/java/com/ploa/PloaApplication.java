package com.ploa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PloaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PloaApplication.class, args);
    }
}
