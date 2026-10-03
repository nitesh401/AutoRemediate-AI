package com.company.autoremediate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AutoRemediateApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutoRemediateApplication.class, args);
    }
}
