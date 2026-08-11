package com.deliveryglance.trackinglink;

import java.time.Instant;

/** The two responses this module serialises. Neither is a domain object. */
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

	/**
	 * The placeholder authorised snapshot. It carries the Delivery Reference and nothing else: the
	 * Reference is Recipient-facing by Ticket 12, and it is enough to prove the grant resolved to the
	 * right Delivery. Everything else the Recipient sees is DG-025's to design.
	 */
	record Snapshot(String deliveryReference) {
	}

}
