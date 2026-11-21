package com.connectBro.ConnectionsService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients
public class ConnectionsServiceApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(ConnectionsServiceApplication.class, args);
	}

}
