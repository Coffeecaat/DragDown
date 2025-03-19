package com.example.DragDown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude={DataSourceAutoConfiguration.class})
public class DragDownApplication {
	public static void main(String[] args) {
		SpringApplication.run(DragDownApplication.class, args);
		System.out.println("Hello World");
	}

}
