package com.deliveryglance.demo;

import com.deliveryglance.delivery.DeliveryProvisioning;
import com.deliveryglance.location.SharedPositionReset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * The demo switch, and the module's non-web beans. They are declared here rather than annotated
 * {@code @Service}, {@code @Repository} and {@code @Component} so that this class alone decides
 * whether they exist.
 *
 * <p>Off unless {@code delivery-glance.demo.reset-enabled} says otherwise, so a deployment that
 * never asked for a data-wiping route is safe by doing nothing. {@code SecurityConfig} reads the
 * same property and refuses {@code POST /api/demo/reset} outright while it is off, so a deployment
 * cannot end up with the route reachable and the beans absent, or the other way round.
 *
 * <p>The one deployment where it is legitimately on is the public portfolio demo box: every row
 * there is fictional by construction ({@link DemoDelivery}), its Dispatcher and Courier credentials
 * are published on the Sign-in page, and losing all of them is the point rather than an accident.
 * Off everywhere a real Delivery could exist.
 *
 * <p>{@link DemoSelfHeal} is the second half of that box's configuration and needs a cron of its
 * own. No schedule is the default, and it is what local Compose and the tests run: the demo then
 * changes only when somebody drives it, and {@code docker compose down -v} is the stronger reset a
 * local demo already has.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(DemoConfig.RESET_ENABLED)
@EnableScheduling
class DemoConfig {

	/**
	 * The switch. {@link DemoResetController} carries the same condition, because a
	 * {@code @RestController} is component-scanned and would otherwise exist regardless of this class.
	 *
	 * <p>{@code SecurityConfig} spells the property out again rather than reading this constant: it
	 * lives outside this package, and widening the constant to reach it would mean publishing the
	 * demo module's switch to the whole application. Two spellings of one property name is the
	 * smaller cost, and {@code DemoResetDisabledTest} is what notices if they ever disagree — with
	 * the switch off it asserts the route is refused, which only holds while both halves say off.
	 */
	static final String RESET_ENABLED = "delivery-glance.demo.reset-enabled";

	/**
	 * How often the demo puts itself back, as a Spring cron expression. Unset means never, and the
	 * bean below is conditional on it rather than reading it as a value: an unscheduled deployment
	 * has no self-heal bean at all, and so cannot reset a demo somebody is recording.
	 */
	static final String RESET_SCHEDULE = "delivery-glance.demo.reset-schedule";

	@Bean
	DemoResetRepository demoResetRepository(JdbcClient jdbcClient) {
		return new DemoResetRepository(jdbcClient);
	}

	@Bean
	DemoReset demoReset(DemoResetRepository repository, DeliveryProvisioning deliveries,
			SharedPositionReset positions) {
		return new DemoReset(repository, deliveries, positions);
	}

	/**
	 * The condition is an expression rather than {@code @ConditionalOnProperty} because unset here
	 * means blank, not missing: {@code application.yml} gives the property an empty default so the
	 * deployment can set it under a readable environment variable name, and a present-but-empty
	 * property satisfies {@code @ConditionalOnProperty}. The bean would then be built with an empty
	 * cron and the application would refuse to start — on every deployment that had turned the demo
	 * on and asked for no schedule at all.
	 */
	@Bean
	@ConditionalOnExpression("'${" + DemoConfig.RESET_SCHEDULE + ":}'.length() > 0")
	DemoSelfHeal demoSelfHeal(DemoReset demoReset, UserDetailsService accounts,
			// The Dispatcher the self-heal acts as is the account Flyway seeded, so this reads the
			// placeholder that seeded it rather than repeating the address and becoming a second
			// place to change it.
			@Value("${spring.flyway.placeholders.demoDispatcherEmail}") String dispatcherEmail) {
		return new DemoSelfHeal(demoReset, accounts, dispatcherEmail);
	}

}
