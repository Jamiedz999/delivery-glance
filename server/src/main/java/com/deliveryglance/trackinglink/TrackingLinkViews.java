package com.deliveryglance.trackinglink;

import java.time.Instant;

/** The one response this module serialises. It is not a domain object. */
final class TrackingLinkViews {

	private TrackingLinkViews() {
	}

	/**
	 * @param url the shareable Tracking Link, fragment and all. This is the one response in the
	 * application that contains a raw capability, which is why its endpoint is {@code no-store} and
	 * why nothing logs a response body.
	 * @param expiresAt when the link stops working. Returned so a Dispatcher copying a link on its
	 * last day can see that, rather than distributing something that will be unavailable by the time
	 * the Recipient opens it. Core has no Reissue, so this is information, not an action.
	 */
	record CopiedLink(String url, Instant expiresAt) {
	}

}
