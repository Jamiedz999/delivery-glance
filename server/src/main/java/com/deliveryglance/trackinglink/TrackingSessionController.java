package com.deliveryglance.trackinglink;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint that turns a presented capability into a grant.
 *
 * <p>Authorization here is the fragment token and nothing else, and what the resulting cookie is
 * worth is settled by {@link LinkHolderAuthorization} rather than by any role. A signed-in
 * Dispatcher who calls this gets the same generic refusal as a stranger: ADR 06 says the capability
 * authorizes reading one Delivery, and an Internal Account role is a different question with a
 * different answer. The reverse holds too — a grant carries no authority anywhere else in the API.
 */
@RestController
class TrackingSessionController {

	private final TrackingLinks links;

	private final TrackingGrants grants;

	private final TrackingAttempts attempts;

	TrackingSessionController(TrackingLinks links, TrackingGrants grants, TrackingAttempts attempts) {
		this.links = links;
		this.grants = grants;
		this.attempts = attempts;
	}

	@PostMapping("/api/tracking-session")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void exchange(@RequestBody Exchange request, HttpServletRequest httpRequest, HttpServletResponse response) {
		String source = sourceOf(httpRequest);
		// Charged before the attempt runs, not after it fails: checking the budget and spending it
		// in two steps lets concurrent guesses all read the same count and all pass. A success
		// refunds it below.
		if (!this.attempts.tryAttempt(source)) {
			// Same refusal as an unknown token: a throttled guesser learns only that they are being
			// throttled, which they can see from the timing anyway.
			throw new UnavailableLinkException();
		}

		TrackingLinks.GrantIssued issued = this.links.exchange(request.token());

		this.attempts.recordSuccess(source);
		this.grants.issue(response, issued.secret(), issued.expiresAt(), issued.establishedAt());
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
