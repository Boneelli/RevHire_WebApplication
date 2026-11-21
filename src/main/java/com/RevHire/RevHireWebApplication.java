package com.RevHire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class RevHireWebApplication {

	public static void main(final String[] args) {
		SpringApplication.run(RevHireWebApplication.class, args);
	}

}
