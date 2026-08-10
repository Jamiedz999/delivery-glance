package com.deliveryglance.identityaccess;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class IdentityAccessConfig {

	/**
	 * Stored hashes carry their algorithm as a {@code {id}} prefix, so the seeded bcrypt hashes can
	 * be re-hashed with a stronger algorithm later without a flag day.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
