package com.truesignal.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrueSignalServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrueSignalServerApplication.class, args);
    }
}
