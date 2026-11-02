package com.ohchurus.budget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BudgetServiceApplication {

    public static void main(String[] args) {
        /* El hogar vive en Colombia. Con la JVM en UTC, "hoy" cambiaba a las
           7 de la noche: el 27 por la noche ya empezaba el periodo nuevo y se
           generaban sus pendientes antes de tiempo. APP_TZ permite otra zona. */
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(
                System.getenv().getOrDefault("APP_TZ", "America/Bogota")));
        SpringApplication.run(BudgetServiceApplication.class, args);
    }
}
