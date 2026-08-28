package com.deliveryglance.eta;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ETA Window arithmetic ADR 05 pins down: a point estimate widened to a provisional or current
 * window, both endpoints rounded <em>outward</em> to five-minute boundaries, plus the jitter guard
 * that only lets a recalculation move a published window when an endpoint shifts by five minutes.
 */
class EtaWindowsTest {

	private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

	@Test
	void assignedWidensBySkillHalfWidthAndAddsTheFixedPickupBuffer() {
		// Assigned = courier→pickup + 5-minute pickup buffer + pickup→handoff. Here the two legs sum
		// to 20 minutes; the fixed buffer pushes the point estimate to 09:25 (without it, 09:20). A
		// ten-minute half-width gives [09:15, 09:35], both already on five-minute boundaries.
		EtaWindow window = EtaWindows.from(NOW, Duration.ofMinutes(20), EtaPhase.ASSIGNED);

		assertThat(window.start()).isEqualTo(Instant.parse("2026-08-10T09:15:00Z"));
		assertThat(window.end()).isEqualTo(Instant.parse("2026-08-10T09:35:00Z"));
	}

	@Test
	void roundsBothEndpointsOutwardToFiveMinuteBoundaries() {
		// Assigned, 12min of travel + the 5-minute buffer → point estimate 09:17. Half-width 10 gives
		// the raw window [09:07, 09:27]; outward rounding floors the start to 09:05 and ceils the end
		// to 09:30, so the true estimate always stays enclosed.
		EtaWindow window = EtaWindows.from(NOW, Duration.ofMinutes(12), EtaPhase.ASSIGNED);

		assertThat(window.start()).isEqualTo(Instant.parse("2026-08-10T09:05:00Z"));
		assertThat(window.end()).isEqualTo(Instant.parse("2026-08-10T09:30:00Z"));
	}

	@Test
	void inTransitUsesTheTighterTenMinuteWindowWithNoBuffer() {
		// In Transit = current location → handoff only, no buffer. 8min → point 09:08, five-minute
		// half-width → raw [09:03, 09:13] → outward [09:00, 09:15].
		EtaWindow window = EtaWindows.from(NOW, Duration.ofMinutes(8), EtaPhase.IN_TRANSIT);

		assertThat(window.start()).isEqualTo(Instant.parse("2026-08-10T09:00:00Z"));
		assertThat(window.end()).isEqualTo(Instant.parse("2026-08-10T09:15:00Z"));
	}

	@Test
	void treatsAWindowAsUnmovedUntilAnEndpointShiftsAFullFiveMinutes() {
		EtaWindow published = new EtaWindow(Instant.parse("2026-08-10T09:00:00Z"),
				Instant.parse("2026-08-10T09:20:00Z"));

		// Endpoints four minutes off in either direction is jitter — the page keeps the old window.
		EtaWindow jitter = new EtaWindow(Instant.parse("2026-08-10T09:04:00Z"),
				Instant.parse("2026-08-10T09:24:00Z"));
		assertThat(EtaWindows.moved(published, jitter)).isFalse();

		// A full five-minute shift on either endpoint is a real move.
		EtaWindow shiftedEnd = new EtaWindow(Instant.parse("2026-08-10T09:00:00Z"),
				Instant.parse("2026-08-10T09:25:00Z"));
		assertThat(EtaWindows.moved(published, shiftedEnd)).isTrue();

		EtaWindow shiftedStart = new EtaWindow(Instant.parse("2026-08-10T08:55:00Z"),
				Instant.parse("2026-08-10T09:20:00Z"));
		assertThat(EtaWindows.moved(published, shiftedStart)).isTrue();
	}

}
