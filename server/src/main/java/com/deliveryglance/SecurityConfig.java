package com.deliveryglance;

import com.deliveryglance.identityaccess.InternalAccountRole;
import com.deliveryglance.shared.ApiProblemResponses;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
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

	/**
	 * @param logoutHandlers module-supplied cleanup that must happen when a session ends, such as
	 * forgetting a Courier's shared location. Spring Security's own handlers are not beans, so this
	 * holds only the application's — and none at all in a slice test that loads one controller.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<LogoutHandler> logoutHandlers)
			throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.GET, "/api/system").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/session/login").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/session").authenticated()
				.requestMatchers("/api/deliveries", "/api/deliveries/**")
					.hasRole(InternalAccountRole.DISPATCHER.name())
				.requestMatchers("/api/couriers/**").hasRole(InternalAccountRole.COURIER.name())
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
					CsrfCookieFilter.issueCookie(request);
					response.setStatus(HttpStatus.NO_CONTENT.value());
				})
				.failureHandler((request, response, exception) -> ApiProblemResponses.write(response,
						HttpStatus.UNAUTHORIZED, "invalid-credentials", "Sign-in failed",
						"The email and password do not match an enabled Internal Account.")));

		http.logout(logout -> {
			logoutHandlers.orderedStream().forEach(logout::addLogoutHandler);
			logout.logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.DELETE, "/api/session"))
				.logoutSuccessHandler((request, response, authentication) -> {
					CsrfCookieFilter.issueCookie(request);
					response.setStatus(HttpStatus.NO_CONTENT.value());
				});
		});

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

	private CsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(this.secureCookies));
		return repository;
	}

}
