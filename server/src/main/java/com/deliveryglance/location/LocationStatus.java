package com.deliveryglance.location;

import java.time.Instant;

/**
 * A Courier's stored position with the coordinates left out: how fresh it is, when the device
 * measured it, and how tight the reading was. This is what role-facing status DTOs receive; the
 * separate dispatch-only read is constrained to distance ranking and never reaches an HTTP DTO.
 *
 * @param recordedAt null exactly when the freshness is {@link LocationFreshness#UNAVAILABLE}
 */
public record LocationStatus(LocationFreshness freshness, Instant recordedAt, Double accuracyMetres) {

	static LocationStatus unavailable() {
		return new LocationStatus(LocationFreshness.UNAVAILABLE, null, null);
	}

}
