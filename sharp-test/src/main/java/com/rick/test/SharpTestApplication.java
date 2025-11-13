package com.rick.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.DependsOn;

@SpringBootApplication
@DependsOn("entityDAOSupport")
public class SharpTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(SharpTestApplication.class, args);
	}

}
