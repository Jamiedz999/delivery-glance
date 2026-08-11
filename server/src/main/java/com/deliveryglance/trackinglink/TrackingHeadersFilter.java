package com.deliveryglance.trackinglink;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the tracking response headers on every response from a tracking route, whoever wrote it.
 *
 * <p>Having each handler apply them looked equivalent and was not: a request refused by Spring
 * Security — a rejected CSRF token, an authentication entry point — never reaches a handler, so those
 * responses went out with no {@code no-store} and no {@code Referrer-Policy} at all. The requirement
 * is "all tracking responses", and the only place that can honestly mean all of them is in front of
 * the security chain rather than behind it.
 *
 * <p>Hence {@code HIGHEST_PRECEDENCE}: Spring Security's chain sits at -100, and a filter ordered
 * after it does not run when that chain short-circuits. The headers are the same for every request
 * to these paths, so setting them before anything is decided costs nothing and cannot be skipped.
 * The bootstrap page overwrites the one header it needs wider, its own CSP.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TrackingHeadersFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !isTrackingRoute(request.getRequestURI());
	}

	/**
	 * Copy is included although it lives under the Delivery's URL and is a Dispatcher route: it is
	 * the one response in the application whose body contains a raw capability, so it is the last
	 * one that should be storable by a cache or able to leak a referrer.
	 */
	private static boolean isTrackingRoute(String path) {
		return path.equals("/track") || path.startsWith("/track/") || path.equals("/api/tracking-session")
				|| path.startsWith("/api/tracking/") || path.endsWith("/tracking-link/copy");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		TrackingResponseHeaders.apply(response);
		filterChain.doFilter(request, response);
	}

}
