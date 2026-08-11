package com.deliveryglance.trackinglink;

/**
 * A Dispatcher asked to copy the link of a Delivery that has none. Unlike {@link
 * UnavailableLinkException} this is safe to name plainly: the caller is an authenticated Dispatcher
 * who may already list every Delivery, so "no such link" tells them nothing they could not look up.
 */
class TrackingLinkNotFoundException extends RuntimeException {

	TrackingLinkNotFoundException() {
		super("No Tracking Link exists for that Delivery.");
	}

}
