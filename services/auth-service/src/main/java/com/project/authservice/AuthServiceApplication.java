package com.project.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scan both the local {@code com.project.authservice} package and the shared
 * {@code com.project.common} library so the global exception handler, common
 * security helpers and event types are wired automatically.
 */
@SpringBootApplication(scanBasePackages = {"com.project.authservice", "com.project.common"})
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
