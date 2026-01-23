package com.concurrency.poc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableResilientMethods
public class ConcurrencyControlPocApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConcurrencyControlPocApplication.class, args);
	}

}
