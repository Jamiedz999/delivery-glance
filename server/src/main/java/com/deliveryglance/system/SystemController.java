package com.deliveryglance.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SystemController {

	private final String applicationName;

	SystemController(@Value("${spring.application.name}") String applicationName) {
		this.applicationName = applicationName;
	}

	@GetMapping("/api/system")
	SystemStatus systemStatus() {
		return new SystemStatus(applicationName, "ok");
	}

}
