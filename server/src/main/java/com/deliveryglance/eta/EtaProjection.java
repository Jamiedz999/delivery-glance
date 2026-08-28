package com.deliveryglance.eta;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The one thing the Recipient view asks of eta: the current window for a Delivery, if there is one.
 * It is a stored projection, never a live provider call — a Recipient's page load must not wait on
 * or be broken by a travel-time service, so the read only returns what the last successful
 * recalculation left behind.
 *
 * <p>The snapshot carries the two endpoints and when it was last calculated, and nothing else. Both
 * staleness ("last calculated X ago", then unavailable past five minutes) and the running-late
 * condition are derived by the browser from these three instants against its own clock, exactly as
 * Location Freshness is — so a page nobody is refreshing still ages its ETA honestly.
 */
public interface EtaProjection {

	Optional<EtaSnapshot> currentEta(UUID deliveryId);

	/**
	 * @param windowStart the earliest estimated handoff time, on a five-minute boundary
	 * @param windowEnd the latest estimated handoff time; the browser shows "running later than
	 * expected" once its clock passes this
	 * @param calculatedAt when the provider last answered successfully; the browser ages this to
	 * decide when the window is stale and when it must be withdrawn
	 */
	record EtaSnapshot(Instant windowStart, Instant windowEnd, Instant calculatedAt) {
	}

}
