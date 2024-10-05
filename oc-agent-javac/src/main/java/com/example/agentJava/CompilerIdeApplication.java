package com.example.agentJava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
public class CompilerIdeApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompilerIdeApplication.class, args);
    }
}
