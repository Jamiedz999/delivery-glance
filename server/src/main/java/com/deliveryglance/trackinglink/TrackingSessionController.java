package com.deliveryglance.trackinglink;

import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two endpoints a Link Holder's browser talks to.
 *
 * <p>Authorization here is the grant cookie and only the grant cookie. A signed-in Dispatcher who
 * calls these gets the same generic refusal as a stranger: ADR 06 says the capability authorizes
 * reading one Delivery, and an Internal Account role is a different question with a different
 * answer. The reverse holds too — a grant carries no authority anywhere else in the API.
 */
@RestController
class TrackingSessionController {

	private final TrackingLinks links;

	private final TrackingGrants grants;

	private final TrackingAttempts attempts;

	private final Clock clock;

	TrackingSessionController(TrackingLinks links, TrackingGrants grants, TrackingAttempts attempts, Clock clock) {
		this.links = links;
		this.grants = grants;
		this.attempts = attempts;
		this.clock = clock;
	}

	@PostMapping("/api/tracking-session")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void exchange(@RequestBody Exchange request, HttpServletRequest httpRequest, HttpServletResponse response) {
		TrackingResponseHeaders.apply(response);

		String source = sourceOf(httpRequest);
		if (!this.attempts.allow(source)) {
			// Same refusal as an unknown token: a throttled guesser learns only that they are being
			// throttled, which they can see from the timing anyway.
			throw new UnavailableLinkException();
		}

		TrackingLinks.GrantIssued issued;
		try {
			issued = this.links.exchange(request.token());
		}
		catch (UnavailableLinkException ex) {
			this.attempts.recordFailure(source);
			throw ex;
		}

		this.attempts.recordSuccess(source);
		this.grants.issue(response, issued.secret(), issued.expiresAt(), this.clock.instant());
	}

	@GetMapping("/api/tracking/snapshot")
	TrackingLinkViews.Snapshot snapshot(HttpServletRequest request, HttpServletResponse response) {
		TrackingResponseHeaders.apply(response);
		String secret = this.grants.presentedSecret(request).orElseThrow(UnavailableLinkException::new);
		try {
			return this.links.snapshotFor(secret);
		}
		catch (UnavailableLinkException ex) {
			// The cookie is no longer worth anything, so the browser stops sending it rather than
			// retrying with it on every reconnect.
			this.grants.clear(response);
			throw ex;
		}
	}

	/**
	 * The limiter key. It is the direct peer address rather than a forwarded header, because nothing
	 * in Core's deployment sets one and trusting a client-supplied header would hand an attacker the
	 * ability to pick their own bucket.
	 */
	private static String sourceOf(HttpServletRequest request) {
		String address = request.getRemoteAddr();
		return (address != null) ? address : "unknown";
	}

	/**
	 * @param token the raw capability, in the body and never the URL. Deliberately carries no Bean
	 * Validation annotation: the shared validation advice answers a rejected body with a field-level
	 * 400, and "your token is the wrong shape" is a different response from "unknown link". They have
	 * to be the same response, so the shape check happens inside the exchange instead.
	 */
	record Exchange(String token) {
	}

}
