package com.findit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the FindIT Smart Campus Lost and Found Management System.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FindItApplication {

    public static void main(String[] args) {
        SpringApplication.run(FindItApplication.class, args);
    }
}
