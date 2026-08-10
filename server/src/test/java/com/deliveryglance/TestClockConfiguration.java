package com.deliveryglance;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Puts the whole application on a clock the test moves by hand. Location rules turn on thirty
 * seconds and two minutes of elapsed time, and a test that waited for those in real time would be
 * both slow and flaky.
 *
 * <p>Import it alongside {@link IntegrationTest} in the classes that need it; the others keep the
 * real clock, and their Spring context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	/** Fictional, and fixed, so a failure message reads the same on every run. */
	public static final Instant START = Instant.parse("2026-08-10T09:00:00Z");

	/**
	 * Registered under its own name and marked primary rather than replacing the application's
	 * {@code clock} bean, so nothing has to switch bean-definition overriding on.
	 */
	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(START);
	}

}
