package com.deliveryglance.location;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The three deliberately narrow location reads peer modules need: coordinate-free status for the
 * Courier workspace, one ephemeral coordinate snapshot used only for dispatch distance ranking, and
 * the one position a Recipient may see on a map.
 *
 * <p>The two coordinate reads are separate methods although they read the same snapshot, because
 * they answer to different rules and only one of them may ever reach a browser. Merging them would
 * leave the module unable to say which caller it was serving.
 */
public interface LocationFacts {

	CourierLocationFacts factsFor(UUID courierAccountId);

	/**
	 * The one internal coordinate read Core needs: dispatch ranks a Courier against a pickup. It is
	 * never returned by an HTTP DTO, logged or stored durably. The read is freshness-filtered here
	 * rather than by the caller: a position past the point where its coordinates are kept is simply
	 * absent, so Location Freshness stays one rule owned by one module.
	 */
	Optional<DispatchPosition> positionForDispatch(UUID courierAccountId);

	/**
	 * The position a Tracking Link may put on a map, and the only coordinate read whose result is
	 * meant to leave the server. It is empty whenever the Courier is not sharing, has stopped, or
	 * has no reading inside the usable limit — Stop therefore withdraws the coordinates on the very
	 * next read rather than on a later sweep.
	 *
	 * <p>Deciding whether the Delivery is In Transit is emphatically not this module's job; location
	 * knows nothing about Deliveries. The caller asks only once it has established that.
	 */
	Optional<TrackedPosition> positionForTracking(UUID courierAccountId);

	/**
	 * @param sharingStartedAt when the Courier's current session started, or {@code null} if none
	 * is running
	 */
	record CourierLocationFacts(Instant sharingStartedAt, LocationStatus location) {
	}

	record DispatchPosition(double latitude, double longitude) {
	}

	/**
	 * @param recordedAt when the device measured it. It travels with the coordinates because a
	 * position without its measurement time is exactly the false precision ADR 05 forbids: the
	 * reader has no way to present it as anything other than current.
	 */
	record TrackedPosition(double latitude, double longitude, double accuracyMetres, Instant recordedAt) {
	}

}
