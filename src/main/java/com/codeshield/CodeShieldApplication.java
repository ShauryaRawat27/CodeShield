package com.codeshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeShieldApplication.class, args);
    }
}
