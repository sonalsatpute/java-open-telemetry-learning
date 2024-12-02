package com.sonalsatpute.http_metrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sonalsatpute")
public class HttpMetricsApplication {

	public static void main(String[] args) {
		SpringApplication.run(HttpMetricsApplication.class, args);
	}

}
