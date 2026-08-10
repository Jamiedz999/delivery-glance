package com.deliveryglance.location;

import java.time.Instant;
import java.util.UUID;

/**
 * The two deliberately narrow location reads peer modules need: coordinate-free status for the
 * Courier workspace, and one ephemeral coordinate snapshot used only for dispatch distance ranking.
 */
public interface LocationFacts {

	CourierLocationFacts factsFor(UUID courierAccountId);

	/**
	 * The one internal coordinate read Core needs: dispatch ranks a Courier against a pickup. It is
	 * never returned by an HTTP DTO, logged or stored durably.
	 */
	java.util.Optional<DispatchPosition> positionForDispatch(UUID courierAccountId);

	/**
	 * @param sharingStartedAt when the Courier's current session started, or {@code null} if none
	 * is running
	 */
	record CourierLocationFacts(Instant sharingStartedAt, LocationStatus location) {
	}

	record DispatchPosition(double latitude, double longitude, Instant recordedAt) {
	}

}
