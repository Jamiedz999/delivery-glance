package com.deliveryglance.trackinglink;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The cookie that carries a Tracking grant.
 *
 * <p>Its properties are the point. {@code HttpOnly} keeps it away from page JavaScript, which is
 * what makes the third-party-script rule for /track enforceable rather than aspirational.
 * {@code SameSite=Lax} rather than {@code Strict} because a Recipient reaches this page by following
 * a link from a message app, and Strict would drop the cookie on exactly that navigation. Host-only
 * — no {@code Domain} attribute — so it is never sent to a sibling hostname.
 *
 * <p>It is a separate cookie from the Internal Account session, under a different name, and neither
 * is consulted when the other is being authorized.
 */
@Component
class TrackingGrants {

	static final String COOKIE_NAME = "DG_TRACKING";

	private final TrackingLinkProperties properties;

	TrackingGrants(TrackingLinkProperties properties) {
		this.properties = properties;
	}

	void issue(HttpServletResponse response, String secret, Instant expiresAt, Instant now) {
		response.addHeader(HttpHeaders.SET_COOKIE,
				cookie(secret).maxAge(Duration.between(now, expiresAt)).build().toString());
	}

	void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("").maxAge(0).build().toString());
	}

	Optional<String> presentedSecret(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
				return Optional.of(cookie.getValue());
			}
		}
		return Optional.empty();
	}

	private ResponseCookie.ResponseCookieBuilder cookie(String value) {
		return ResponseCookie.from(COOKIE_NAME, value)
			.httpOnly(true)
			.secure(this.properties.cookieSecure())
			.sameSite("Lax")
			.path("/");
	}

}
