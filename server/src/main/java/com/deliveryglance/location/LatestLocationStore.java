package com.deliveryglance.location;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * The only place a Courier coordinate exists in this application. It holds at most one complete
 * snapshot per Courier and never appends, so there is nothing here that could become a Route
 * History — and, because the snapshots live in process memory, nothing that survives a restart.
 *
 * <p>Deciding whether a report replaces the snapshot belongs here rather than to a caller, because
 * the comparison and the replacement have to happen as one step.
 *
 * <p>There is deliberately no interface in front of this class. A second implementation was
 * expected when the seam was planned, but tests get their determinism from the injected
 * {@link Clock} instead, and an interface with one implementation would only be a pass-through.
 */
@Component
class LatestLocationStore {

	/** A radius wider than this describes a neighbourhood, not a Courier. */
	private static final double USABLE_ACCURACY_METRES = 100.0;

	/** How far ahead of the server a device clock may run before its reading is not believable. */
	private static final Duration TOLERATED_CLOCK_SKEW = Duration.ofSeconds(30);

	private final ConcurrentMap<UUID, LatestLocation> snapshots = new ConcurrentHashMap<>();

	private final Clock clock;

	LatestLocationStore(Clock clock) {
		this.clock = clock;
	}

	/** Applies the acceptance contract and, if the reading wins, replaces the whole snapshot. */
	ReportOutcome record(LocationReport report) {
		Instant now = this.clock.instant();
		if (report.accuracyMetres() > USABLE_ACCURACY_METRES) {
			return ReportOutcome.REJECTED_LOW_ACCURACY;
		}
		if (Duration.between(now, report.recordedAt()).compareTo(TOLERATED_CLOCK_SKEW) > 0) {
			return ReportOutcome.REJECTED_FUTURE_DATED;
		}
		if (Duration.between(report.recordedAt(), now).compareTo(LocationFreshness.USABLE_LIMIT) > 0) {
			return ReportOutcome.REJECTED_STALE;
		}

		LatestLocation candidate = new LatestLocation(report.generation(), report.longitude(), report.latitude(),
				report.accuracyMetres(), report.recordedAt(), now);
		// Comparing and replacing inside compute() keeps two concurrent reports from both reading
		// the same stored snapshot and the older one winning by finishing last.
		LatestLocation kept = this.snapshots.compute(report.courierAccountId(),
				(courierAccountId, stored) -> supersedes(candidate, stored) ? candidate : stored);
		return (kept == candidate) ? ReportOutcome.ACCEPTED : ReportOutcome.REJECTED_NOT_NEWER;
	}

	/**
	 * The Courier's usable position, if one exists. A snapshot past the two-minute boundary is
	 * removed by this call rather than merely hidden from it.
	 */
	Optional<LatestLocation> current(UUID courierAccountId) {
		Instant now = this.clock.instant();
		// The read deletes an expired snapshot instead of merely hiding it, so the two-minute
		// boundary holds even if the sweep below never runs.
		return Optional.ofNullable(this.snapshots.computeIfPresent(courierAccountId,
				(courier, stored) -> usable(stored, now) ? stored : null));
	}

	/** Drops the Courier's coordinates now, for Stop, sign-out and a new Location Sharing Session. */
	void forget(UUID courierAccountId) {
		this.snapshots.remove(courierAccountId);
	}

	/** Drops every snapshot, expired or not. See {@link SharedPositionReset} for its one caller. */
	void forgetEveryCourier() {
		this.snapshots.clear();
	}

	/** Drops every expired snapshot, so unread coordinates do not outlive their two minutes. */
	void forgetExpired() {
		Instant now = this.clock.instant();
		this.snapshots.values().removeIf((stored) -> !usable(stored, now));
	}

	/**
	 * Current Location is the newest measurement, whichever Location Sharing Session produced it.
	 * The session a snapshot belongs to is recorded but never compared: a session that ends takes
	 * its snapshot with it, so ordering never has to arbitrate between two of them.
	 */
	private static boolean supersedes(LatestLocation candidate, LatestLocation stored) {
		if (stored == null) {
			return true;
		}
		int order = candidate.recordedAt().compareTo(stored.recordedAt());
		return (order > 0) || (order == 0 && candidate.accuracyMetres() < stored.accuracyMetres());
	}

	private static boolean usable(LatestLocation stored, Instant now) {
		return LocationFreshness.of(stored.recordedAt(), now) != LocationFreshness.UNAVAILABLE;
	}

}
