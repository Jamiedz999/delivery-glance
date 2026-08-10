package com.deliveryglance.location;

import java.time.Instant;

/**
 * A Courier's stored position with the coordinates left out: how fresh it is, when the device
 * measured it, and how tight the reading was. This is the whole of what leaves the module, and it
 * is one type rather than one per audience so that no caller can start describing a position in
 * terms the others do not share.
 *
 * @param recordedAt null exactly when the freshness is {@link LocationFreshness#UNAVAILABLE}
 */
public record LocationStatus(LocationFreshness freshness, Instant recordedAt, Double accuracyMetres) {

	static LocationStatus unavailable() {
		return new LocationStatus(LocationFreshness.UNAVAILABLE, null, null);
	}

}
