package com.deliveryglance.trackinglink;

/**
 * ADR 06's Tracking Link Change Reason: the structured explanation a Dispatcher gives when they
 * recover a Tracking Link. The full set is shared across Rotation, Revocation and Reissue; which
 * values a given action accepts is that action's rule, not the enum's — the glossary calls this
 * "filtered to the applicable action".
 *
 * <p>{@code OTHER} is only accepted together with a note, so the history never records an
 * unexplained recovery.
 */
enum TrackingLinkChangeReason {

	WRONG_RECIPIENT,
	SUSPECTED_EXPOSURE,
	RECIPIENT_REQUEST,
	ACCESS_NO_LONGER_NEEDED,
	DELIVERY_STILL_ACTIVE,
	OTHER;

	/**
	 * Whether this reason may be given for a Revocation. Every reason applies except {@link
	 * #DELIVERY_STILL_ACTIVE}: that one says the holder still needs access, which is a reason to
	 * Reissue a replacement link, not to end access without one.
	 */
	boolean appliesToRevocation() {
		return this != DELIVERY_STILL_ACTIVE;
	}

}
