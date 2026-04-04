package com.ohchurus.fasting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class FastingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FastingServiceApplication.class, args);
    }
}
