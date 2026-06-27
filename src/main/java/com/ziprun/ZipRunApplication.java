package com.ziprun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the ZipRun backend.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ZipRunApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZipRunApplication.class, args);
    }
}
