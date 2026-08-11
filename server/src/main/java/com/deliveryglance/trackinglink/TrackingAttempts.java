package com.deliveryglance.trackinglink;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slows a source down after repeated failed exchanges. The 256-bit capability is what actually makes
 * guessing hopeless; this exists so a guesser is also visibly wasting their time, and so the failure
 * path cannot be hammered for free.
 *
 * <p>Two things it deliberately does not do. It never marks a link as compromised, expired or
 * disabled: failed guesses against a valid link would then be a way of denying its Recipient access.
 * And it never grows without limit — the map evicts its oldest entry past a fixed size, so rotating
 * source addresses costs the attacker addresses rather than costing the application memory.
 *
 * <p>Per-instance and in-memory, which is the whole of what Core needs. Cross-instance limiting is
 * explicitly a non-goal of DG-024.
 */
final class TrackingAttempts {

	static final int FAILURES_PER_SOURCE = 10;

	static final Duration WINDOW = Duration.ofMinutes(1);

	static final int TRACKED_SOURCES = 10_000;

	private final Clock clock;

	private final Map<String, Failures> bySource;

	TrackingAttempts(Clock clock) {
		this.clock = clock;
		this.bySource = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Failures> eldest) {
				return size() > TRACKED_SOURCES;
			}
		};
	}

	synchronized boolean allow(String source) {
		Failures failures = this.bySource.get(source);
		return failures == null || failures.countAt(this.clock.instant()) < FAILURES_PER_SOURCE;
	}

	synchronized void recordFailure(String source) {
		Instant now = this.clock.instant();
		Failures failures = this.bySource.get(source);
		if (failures == null || failures.hasLapsedAt(now)) {
			this.bySource.put(source, new Failures(now, 1));
			return;
		}
		this.bySource.put(source, failures.increment());
	}

	/** Holding the real capability settles it: those earlier failures were not an attack. */
	synchronized void recordSuccess(String source) {
		this.bySource.remove(source);
	}

	synchronized int trackedSourceCount() {
		return this.bySource.size();
	}

	/**
	 * A fixed window rather than a sliding one: the extra precision would buy nothing here, and the
	 * simpler rule is one anybody reading the throttle can hold in their head.
	 */
	private record Failures(Instant windowStart, int count) {

		boolean hasLapsedAt(Instant now) {
			return Duration.between(this.windowStart, now).compareTo(WINDOW) > 0;
		}

		int countAt(Instant now) {
			return hasLapsedAt(now) ? 0 : this.count;
		}

		Failures increment() {
			return new Failures(this.windowStart, this.count + 1);
		}

	}

}
