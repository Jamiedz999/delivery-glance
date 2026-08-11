package com.deliveryglance.trackinglink;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrackingLinkProperties.class)
class TrackingLinkConfig {

	@Bean
	TrackingCapabilities trackingCapabilities(TrackingLinkProperties properties) {
		Map<Integer, byte[]> keys = properties.keys()
			.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().getBytes(StandardCharsets.UTF_8)));
		return new TrackingCapabilities(keys, properties.currentKeyVersion());
	}

	/**
	 * One limiter for the whole application, so the memory bound is a total rather than something an
	 * attacker multiplies by opening more connections.
	 */
	@Bean
	TrackingAttempts trackingAttempts(Clock clock) {
		return new TrackingAttempts(clock);
	}

}
