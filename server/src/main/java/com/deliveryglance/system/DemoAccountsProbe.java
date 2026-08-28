package com.deliveryglance.system;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Answers one question for the Sign-in page: are the two Internal Accounts still the fictional ones
 * the first Flyway migration seeds? Only when they are may the page publish their credentials.
 *
 * <p>The answer is a string comparison of each row's stored password hash against the factory hash
 * compiled in here — deliberately not a bcrypt verification, which would need the plaintext password
 * server-side and hang a cost-12 hash off a {@code permitAll} probe. A deployment that set its own
 * {@code DEMO_*_PASSWORD_HASH} (or {@code DEMO_*_EMAIL}) seeded different rows, so the comparison is
 * false and the page says it cannot supply credentials.
 *
 * <p>Computed once at startup and cached: Core has no registration, password recovery or account
 * administration, so these two rows cannot change while the application runs.
 */
@Component
class DemoAccountsProbe {

	// The email/hash pairs V1__internal_account.sql seeds with the default Flyway placeholders in
	// application.yml. These are compiled in on purpose: reading them back from configuration would
	// make a deployment that changed the values agree with its own database and wrongly report the
	// accounts unchanged. If the application.yml defaults ever change, SystemControllerTest's
	// factory-rows case fails, which is the reminder to change these together.
	private static final String FACTORY_DISPATCHER_EMAIL = "dispatcher@delivery-glance.example";

	private static final String FACTORY_DISPATCHER_HASH = "{bcrypt}$2a$12$48gmvbvPhSdBdq4T2Q90U.B8zYiNnEfN2sHG5LDmdCYkCbXboDQqK";

	private static final String FACTORY_COURIER_EMAIL = "courier@delivery-glance.example";

	private static final String FACTORY_COURIER_HASH = "{bcrypt}$2a$12$bYalN1xoO/VQTnYbn6NEPuz3YTktkC.bMYNyKNVlEk9.ga5FfGhg.";

	private final boolean unchanged;

	DemoAccountsProbe(JdbcClient jdbcClient) {
		this.unchanged = hashMatches(jdbcClient, FACTORY_DISPATCHER_EMAIL, FACTORY_DISPATCHER_HASH)
				&& hashMatches(jdbcClient, FACTORY_COURIER_EMAIL, FACTORY_COURIER_HASH);
	}

	boolean demoAccountsUnchanged() {
		return this.unchanged;
	}

	private static boolean hashMatches(JdbcClient jdbcClient, String email, String factoryHash) {
		return jdbcClient.sql("SELECT password_hash FROM internal_account WHERE email = :email")
			.param("email", email)
			.query(String.class)
			.optional()
			.map(factoryHash::equals)
			.orElse(false);
	}

}
