package com.raghu.pilliongo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PilliongoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PilliongoApplication.class, args);
	}

}
