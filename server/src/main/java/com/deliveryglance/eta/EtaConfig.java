package com.deliveryglance.eta;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Registers the eta module's deployment inputs, the travel-time provider seam over an HTTP client,
 * and the module's own scheduler.
 *
 * <p>The provider bean exists only when a {@code provider-base-url} is configured. A deployment
 * without one has no {@link TravelTimePort} at all, and {@link EtaCalculations} reaches for it
 * through an {@code ObjectProvider} and computes nothing — so an unconfigured deployment shows no
 * ETA rather than failing to start. The HTTP client carries the per-request timeout that turns a
 * slow provider into a degraded window instead of a hung sweep.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TravelTimeProperties.class)
@EnableScheduling
class EtaConfig {

	@Bean
	@ConditionalOnProperty("delivery-glance.eta.provider-base-url")
	TravelTimePort travelTimePort(TravelTimeProperties properties, Clock clock) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.requestTimeout());
		factory.setReadTimeout(properties.requestTimeout());
		RestClient client = RestClient.builder().requestFactory(factory).build();
		return new HttpTravelTimeAdapter(client, properties, clock);
	}

}
