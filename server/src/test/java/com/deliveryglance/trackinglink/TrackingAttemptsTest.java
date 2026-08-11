package com.deliveryglance.trackinglink;

import java.time.Duration;

import com.deliveryglance.MutableClock;
import com.deliveryglance.TestClockConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Defence in depth behind the 256-bit token, not a substitute for it. The rules that matter are
 * that it throttles a guesser, that it forgets, and that it cannot be turned into a way of locking
 * a Recipient out of a link that is perfectly valid.
 */
class TrackingAttemptsTest {

	private final MutableClock clock = new MutableClock(TestClockConfiguration.START);

	private final TrackingAttempts attempts = new TrackingAttempts(this.clock);

	@Test
	void allowsAHandfulOfFailuresFromOneSourceBeforeThrottlingIt() {
		for (int attempt = 0; attempt < TrackingAttempts.FAILURES_PER_SOURCE; attempt++) {
			assertThat(this.attempts.allow("198.51.100.7")).isTrue();
			this.attempts.recordFailure("198.51.100.7");
		}

		assertThat(this.attempts.allow("198.51.100.7")).isFalse();
	}

	@Test
	void throttlesOnlyTheSourceThatIsGuessing() {
		exhaust("198.51.100.7");

		assertThat(this.attempts.allow("203.0.113.9")).isTrue();
	}

	@Test
	void forgetsFailuresOnceTheWindowHasPassed() {
		exhaust("198.51.100.7");

		this.clock.advance(TrackingAttempts.WINDOW.plusSeconds(1));

		assertThat(this.attempts.allow("198.51.100.7")).isTrue();
	}

	/**
	 * A successful exchange means the caller holds the capability, so whatever the earlier failures
	 * were, they were not an attack on this link.
	 */
	@Test
	void clearsASourcesFailuresWhenItFinallySucceeds() {
		for (int attempt = 0; attempt < TrackingAttempts.FAILURES_PER_SOURCE - 1; attempt++) {
			this.attempts.recordFailure("198.51.100.7");
		}

		this.attempts.recordSuccess("198.51.100.7");
		this.attempts.recordFailure("198.51.100.7");

		assertThat(this.attempts.allow("198.51.100.7")).isTrue();
	}

	/**
	 * The whole point of bounding it. An attacker who rotates source addresses must not be able to
	 * grow this map until the application runs out of memory — that would turn a guessing defence
	 * into the denial of service it exists to prevent.
	 */
	@Test
	void staysBoundedWhenTheSourceAddressKeepsChanging() {
		for (int source = 0; source < TrackingAttempts.TRACKED_SOURCES * 3; source++) {
			this.attempts.recordFailure("198.51.100." + source);
		}

		assertThat(this.attempts.trackedSourceCount()).isLessThanOrEqualTo(TrackingAttempts.TRACKED_SOURCES);
	}

	/**
	 * Nothing in this class touches a link. A guesser who exhausts the budget slows themselves down;
	 * the holder of the real token is unaffected once the window passes.
	 */
	@Test
	void neverExpiresOrDisablesTheLinkItProtects() {
		exhaust("198.51.100.7");
		this.clock.advance(Duration.ofMinutes(30));

		assertThat(this.attempts.allow("198.51.100.7")).isTrue();
	}

	private void exhaust(String source) {
		for (int attempt = 0; attempt < TrackingAttempts.FAILURES_PER_SOURCE; attempt++) {
			this.attempts.recordFailure(source);
		}
	}

}
