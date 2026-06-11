package com.truesignal.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrueSignalMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrueSignalMonitorApplication.class, args);
    }
}
