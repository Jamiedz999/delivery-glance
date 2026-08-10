package com.deliveryglance;

import org.springframework.boot.SpringApplication;

public class TestDeliveryGlanceApplication {

	public static void main(String[] args) {
		SpringApplication.from(DeliveryGlanceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
