package com.smartqueue.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Smart Queue &amp; Token Management Web Application.
 * Shares the same MySQL database and business logic rules as the JavaFX desktop system.
 */
@SpringBootApplication
public class SmartQueueWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartQueueWebApplication.class, args);
    }
}
