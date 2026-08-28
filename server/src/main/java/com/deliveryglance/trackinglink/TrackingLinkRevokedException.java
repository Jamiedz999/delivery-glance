package com.deliveryglance.trackinglink;

/**
 * A Dispatcher tried to act on a link whose access has already ended: Copy a revoked link, or revoke
 * one a second time. Unlike {@link UnavailableLinkException}, this is answered plainly, because its
 * audience is a signed-in Dispatcher who already has full authority over the Delivery — the
 * indistinguishability rule protects the Link Holder, not the operator.
 */
class TrackingLinkRevokedException extends RuntimeException {

	TrackingLinkRevokedException() {
		super("This tracking link has been revoked.");
	}

}
