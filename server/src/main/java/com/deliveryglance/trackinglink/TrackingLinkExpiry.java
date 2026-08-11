package com.deliveryglance.trackinglink;

import java.time.Duration;
import java.time.Instant;

/**
 * When a Tracking Link stops working. Two limits apply and the earlier one wins: seven days from
 * issue, and twenty-four hours from the moment the Delivery reached a terminal state.
 *
 * <p>Neither limit is stored as a single expiry column that something would then have to keep
 * up to date. The seven-day cap is fixed at creation; the terminal grace period is derived from the
 * Delivery's own history every time the link is read, so a Delivery that finishes cannot leave a
 * link valid because nothing got round to shortening it.
 */
final class TrackingLinkExpiry {

	/** How long a Delivery's Recipient may still see the outcome after it ends. */
	static final Duration TERMINAL_GRACE = Duration.ofHours(24);

	private TrackingLinkExpiry() {
	}

	/**
	 * @param issuedExpiry the seven-day cap fixed when the link was created
	 * @param terminalAt when the Delivery reached a terminal state, or {@code null} if it has not
	 */
	static Instant effective(Instant issuedExpiry, Instant terminalAt) {
		if (terminalAt == null) {
			return issuedExpiry;
		}
		Instant graceEnd = terminalAt.plus(TERMINAL_GRACE);
		return graceEnd.isBefore(issuedExpiry) ? graceEnd : issuedExpiry;
	}

	/**
	 * The expiry instant is the first moment the link no longer works, not the last moment it does.
	 *
	 * <p>Which way to round a single instant looks arbitrary, and would be, except that the database
	 * has already chosen: {@code tracking_grant} requires {@code expires_at > established_at}, so a
	 * link exchanged at exactly its expiry would produce a grant with no lifetime at all and the
	 * insert would be refused. Calling the instant valid therefore turns a link that should get one
	 * quiet Unavailable response into a stack trace. Half-open is also the rule everything else here
	 * already assumes: a link is valid for seven days, not seven days and one instant.
	 */
	static boolean isValidAt(Instant expiry, Instant now) {
		return now.isBefore(expiry);
	}

}
