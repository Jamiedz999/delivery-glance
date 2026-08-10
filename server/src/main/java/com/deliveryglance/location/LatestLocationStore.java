package com.deliveryglance.location;

import java.util.Optional;
import java.util.UUID;

/**
 * The only place a Courier coordinate exists in this application. It holds at most one complete
 * snapshot per Courier and never appends, so there is nothing here that could become a Route
 * History, and nothing that survives a restart.
 *
 * <p>Deciding whether a report replaces the snapshot is part of the store rather than of its
 * caller, because the comparison and the replacement have to happen as one step.
 */
interface LatestLocationStore {

	/** Applies the acceptance contract and, if the reading wins, replaces the whole snapshot. */
	ReportOutcome record(LocationReport report);

	/**
	 * The Courier's usable position, if one exists. A snapshot past the two-minute boundary is
	 * removed by this call rather than merely hidden from it.
	 */
	Optional<LatestLocation> current(UUID courierAccountId);

	/** Drops the Courier's coordinates now, for Stop, sign-out and a new Location Sharing Session. */
	void forget(UUID courierAccountId);

	/** Drops every expired snapshot, so unread coordinates do not outlive their two minutes. */
	void forgetExpired();

}
