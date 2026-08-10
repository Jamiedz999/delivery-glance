package com.deliveryglance.shared;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Modules read the time through an injected {@link Clock} rather than {@code Instant.now()}, so a
 * test can pin it without touching the code under test.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
