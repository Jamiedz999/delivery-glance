package com.deliveryglance;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security only loads the CSRF token when something asks for it. The React client has no
 * server-rendered form to read it from, so this filter resolves the token on every request and
 * lets the repository write the cookie the client will echo back as a header.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

	/**
	 * Resolving the deferred token is what makes the repository write its cookie. Sign-in and
	 * sign-out replace the token and end the request before this filter runs, so they call this
	 * themselves rather than leaving the client without a usable one.
	 */
	static void issueCookie(HttpServletRequest request) {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			csrfToken.getToken();
		}
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		issueCookie(request);
		filterChain.doFilter(request, response);
	}

}
