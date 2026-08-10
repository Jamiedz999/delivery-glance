package com.deliveryglance.location;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The thirty-second and two-minute boundaries every role's wording depends on.
 */
class LocationFreshnessTest {

	private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

	@Test
	void callsAReadingLiveThroughThirtySeconds() {
		assertThat(LocationFreshness.of(NOW, NOW)).isEqualTo(LocationFreshness.LIVE);
		assertThat(LocationFreshness.of(NOW.minusSeconds(30), NOW)).isEqualTo(LocationFreshness.LIVE);
	}

	@Test
	void callsAReadingDelayedFromThirtySecondsThroughTwoMinutes() {
		assertThat(LocationFreshness.of(NOW.minusSeconds(31), NOW)).isEqualTo(LocationFreshness.DELAYED);
		assertThat(LocationFreshness.of(NOW.minusSeconds(120), NOW)).isEqualTo(LocationFreshness.DELAYED);
	}

	@Test
	void callsAReadingUnavailableAfterTwoMinutes() {
		assertThat(LocationFreshness.of(NOW.minusSeconds(121), NOW)).isEqualTo(LocationFreshness.UNAVAILABLE);
	}

}
