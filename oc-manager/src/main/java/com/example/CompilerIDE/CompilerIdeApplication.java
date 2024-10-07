package com.example.CompilerIDE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class CompilerIdeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompilerIdeApplication.class, args);
	}

}
