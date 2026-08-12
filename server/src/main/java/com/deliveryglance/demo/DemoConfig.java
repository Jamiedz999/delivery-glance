package com.deliveryglance.demo;

import com.deliveryglance.delivery.DeliveryProvisioning;
import com.deliveryglance.location.SharedPositionReset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The demo switch, and the module's two non-web beans. They are declared here rather than annotated
 * {@code @Service} and {@code @Repository} so that this class alone decides whether they exist.
 *
 * <p>Off unless {@code delivery-glance.demo.reset-enabled} says otherwise, which is what makes a
 * production deployment safe by doing nothing. {@code SecurityConfig} reads the same property and
 * refuses {@code POST /api/demo/reset} outright while it is off, so a deployment cannot end up with
 * the route reachable and the beans absent, or the other way round.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(DemoConfig.RESET_ENABLED)
class DemoConfig {

	/**
	 * The switch, named once. {@link DemoResetController} carries the same condition, because a
	 * {@code @RestController} is component-scanned and would otherwise exist regardless of this class.
	 */
	static final String RESET_ENABLED = "delivery-glance.demo.reset-enabled";

	@Bean
	DemoResetRepository demoResetRepository(JdbcClient jdbcClient) {
		return new DemoResetRepository(jdbcClient);
	}

	@Bean
	DemoReset demoReset(DemoResetRepository repository, DeliveryProvisioning deliveries,
			SharedPositionReset positions) {
		return new DemoReset(repository, deliveries, positions);
	}

}
