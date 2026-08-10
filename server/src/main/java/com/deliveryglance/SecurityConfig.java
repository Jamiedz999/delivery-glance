package com.deliveryglance;

import com.deliveryglance.identityaccess.InternalAccountRole;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Deny-by-default policy. {@code /api/system}, {@code /actuator/health} and the frontend shell are
 * the only public surfaces; every later module adds its own authenticated rule above the frontend
 * catch-all rather than relaxing this default.
 *
 * <p>API callers get {@code 401} when there is no Internal Account session and {@code 403} when the
 * signed-in role is not allowed, so the browser can tell "sign in again" apart from "not for you".
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final boolean secureCookies;

	SecurityConfig(@Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
		this.secureCookies = secureCookies;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.GET, "/api/system").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/session/login").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/session").authenticated()
				.requestMatchers("/api/deliveries", "/api/deliveries/**")
					.hasRole(InternalAccountRole.DISPATCHER.name())
				.requestMatchers("/api/**").denyAll()
				.requestMatchers("/actuator/**").denyAll()
				.requestMatchers(HttpMethod.GET, "/**").permitAll()
				.anyRequest().denyAll());

		http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository())
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

		http.formLogin(login -> login.loginProcessingUrl("/api/session/login")
				.usernameParameter("email")
				.passwordParameter("password")
				.successHandler((request, response, authentication) -> {
					issueRefreshedCsrfCookie(request);
					response.setStatus(HttpStatus.NO_CONTENT.value());
				})
				.failureHandler((request, response, exception) -> ApiProblemResponses.write(response,
						HttpStatus.UNAUTHORIZED, "invalid-credentials", "Sign-in failed",
						"The email and password do not match an enabled Internal Account.")));

		http.logout(logout -> logout
				.logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.DELETE, "/api/session"))
				.logoutSuccessHandler((request, response, authentication) -> {
					issueRefreshedCsrfCookie(request);
					response.setStatus(HttpStatus.NO_CONTENT.value());
				}));

		http.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) -> ApiProblemResponses.write(response,
						HttpStatus.UNAUTHORIZED, "authentication-required", "Authentication required",
						"Sign in with an Internal Account to use this endpoint."))
				.accessDeniedHandler((request, response, exception) -> {
					if (exception instanceof CsrfException) {
						ApiProblemResponses.write(response, HttpStatus.FORBIDDEN, "csrf-token-invalid",
								"Invalid CSRF token", "Reload the page and retry the request.");
						return;
					}
					ApiProblemResponses.write(response, HttpStatus.FORBIDDEN, "access-denied", "Access denied",
							"This Internal Account role cannot use this endpoint.");
				}));

		return http.build();
	}

	/**
	 * Signing in and out replaces the CSRF token, and both filters end the request before
	 * {@link CsrfCookieFilter} would run. Resolving the new token here means the client always
	 * leaves those two calls holding a usable cookie.
	 */
	private static void issueRefreshedCsrfCookie(HttpServletRequest request) {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			csrfToken.getToken();
		}
	}

	private CsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(this.secureCookies));
		return repository;
	}

}
