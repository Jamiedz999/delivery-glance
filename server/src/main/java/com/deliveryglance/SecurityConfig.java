package com.deliveryglance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default policy. {@code /api/system}, {@code /actuator/health} and the frontend shell
 * are the only public surfaces; every later module adds its own authenticated rule above the
 * frontend catch-all rather than relaxing this default.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.GET, "/api/system").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.requestMatchers("/api/**").denyAll()
				.requestMatchers("/actuator/**").denyAll()
				.requestMatchers(HttpMethod.GET, "/**").permitAll()
				.anyRequest().denyAll());
		return http.build();
	}

}
