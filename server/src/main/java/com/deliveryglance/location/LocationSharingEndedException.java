package com.deliveryglance.location;

/**
 * A report named a Location Sharing Session that is no longer the Courier's current one, or failed
 * to prove it holds that session's reporting secret. Either way the answer is the same: this page
 * may not report, and should return to Sharing Off rather than retry.
 */
class LocationSharingEndedException extends RuntimeException {

	LocationSharingEndedException() {
		super("This Location Sharing Session has ended; start sharing again to report a position.");
	}

}
