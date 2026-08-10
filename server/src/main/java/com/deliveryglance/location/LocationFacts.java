package com.deliveryglance.location;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything a peer module may know about a Courier's location: whether a Location Sharing Session
 * is running, and how fresh the position behind it is. Coordinates are deliberately absent — no
 * caller in Core needs them, and an interface that offered them would make it easy to log or store
 * one by accident.
 */
public interface LocationFacts {

	CourierLocationFacts factsFor(UUID courierAccountId);

	/**
	 * @param sharingStartedAt when the Courier's current session started, or {@code null} if none
	 * is running
	 */
	record CourierLocationFacts(Instant sharingStartedAt, LocationStatus location) {
	}

}
