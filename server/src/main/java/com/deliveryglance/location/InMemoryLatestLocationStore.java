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
 * Process memory holds the snapshots, which is the whole point: a restart is meant to leave the
 * product with no Courier coordinates at all rather than with a position it cannot vouch for.
 */
@Component
class InMemoryLatestLocationStore implements LatestLocationStore {

	/** A radius wider than this describes a neighbourhood, not a Courier. */
	private static final double USABLE_ACCURACY_METRES = 100.0;

	/** How far ahead of the server a device clock may run before its reading is not believable. */
	private static final Duration TOLERATED_CLOCK_SKEW = Duration.ofSeconds(30);

	private final ConcurrentMap<UUID, LatestLocation> snapshots = new ConcurrentHashMap<>();

	private final Clock clock;

	InMemoryLatestLocationStore(Clock clock) {
		this.clock = clock;
	}

	@Override
	public ReportOutcome record(LocationReport report) {
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

	@Override
	public Optional<LatestLocation> current(UUID courierAccountId) {
		Instant now = this.clock.instant();
		// The read deletes an expired snapshot instead of merely hiding it, so the two-minute
		// boundary holds even if the sweep below never runs.
		return Optional.ofNullable(this.snapshots.computeIfPresent(courierAccountId,
				(courier, stored) -> usable(stored, now) ? stored : null));
	}

	@Override
	public void forget(UUID courierAccountId) {
		this.snapshots.remove(courierAccountId);
	}

	@Override
	public void forgetExpired() {
		Instant now = this.clock.instant();
		this.snapshots.values().removeIf((stored) -> !usable(stored, now));
	}

	private static boolean supersedes(LatestLocation candidate, LatestLocation stored) {
		if (stored == null) {
			return true;
		}
		// Measurement time only orders readings within one Location Sharing Session. A newly
		// started session describes a Courier the previous one can no longer speak for.
		if (!stored.generation().equals(candidate.generation())) {
			return true;
		}
		int order = candidate.recordedAt().compareTo(stored.recordedAt());
		return (order > 0) || (order == 0 && candidate.accuracyMetres() < stored.accuracyMetres());
	}

	private static boolean usable(LatestLocation stored, Instant now) {
		return LocationFreshness.of(stored.recordedAt(), now) != LocationFreshness.UNAVAILABLE;
	}

}
