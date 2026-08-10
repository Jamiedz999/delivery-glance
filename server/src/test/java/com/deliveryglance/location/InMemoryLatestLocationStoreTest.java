package com.deliveryglance.location;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.deliveryglance.MutableClock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The acceptance contract for a position report, driven by a clock the test moves by hand. Every
 * rule here decides whether a coordinate exists at all, so each one is exercised on both sides of
 * its boundary rather than only in the middle.
 */
class InMemoryLatestLocationStoreTest {

	private static final UUID COURIER = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private static final UUID GENERATION = UUID.fromString("22222222-2222-4222-8222-222222222222");

	private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

	private final MutableClock clock = new MutableClock(NOW);

	private final InMemoryLatestLocationStore store = new InMemoryLatestLocationStore(this.clock);

	@Test
	void keepsTheFirstUsableReading() {
		assertThat(record(NOW, 12.0)).isEqualTo(ReportOutcome.ACCEPTED);

		assertThat(this.store.current(COURIER)).hasValueSatisfying((snapshot) -> {
			assertThat(snapshot.latitude()).isEqualTo(51.5074);
			assertThat(snapshot.longitude()).isEqualTo(-0.1278);
			assertThat(snapshot.accuracyMetres()).isEqualTo(12.0);
			assertThat(snapshot.recordedAt()).isEqualTo(NOW);
			assertThat(snapshot.receivedAt()).isEqualTo(NOW);
		});
	}

	@Test
	void replacesTheSnapshotWithANewerMeasurement() {
		record(NOW.minusSeconds(20), 12.0);

		assertThat(record(NOW.minusSeconds(5), 40.0)).isEqualTo(ReportOutcome.ACCEPTED);
		assertThat(recordedAt()).isEqualTo(NOW.minusSeconds(5));
	}

	@Test
	void rejectsAMeasurementOlderThanTheStoredOne() {
		record(NOW.minusSeconds(5), 12.0);

		assertThat(record(NOW.minusSeconds(20), 5.0)).isEqualTo(ReportOutcome.REJECTED_NOT_NEWER);
		assertThat(recordedAt()).isEqualTo(NOW.minusSeconds(5));
	}

	@Test
	void rejectsARepeatOfTheStoredMeasurement() {
		record(NOW.minusSeconds(5), 12.0);

		assertThat(record(NOW.minusSeconds(5), 12.0)).isEqualTo(ReportOutcome.REJECTED_NOT_NEWER);
	}

	@Test
	void replacesAnEquallyRecentMeasurementOnlyWhenAccuracyImproves() {
		record(NOW.minusSeconds(5), 30.0);

		assertThat(record(NOW.minusSeconds(5), 40.0)).isEqualTo(ReportOutcome.REJECTED_NOT_NEWER);
		assertThat(record(NOW.minusSeconds(5), 10.0)).isEqualTo(ReportOutcome.ACCEPTED);
		assertThat(accuracyMetres()).isEqualTo(10.0);
	}

	@Test
	void rejectsAReadingLessAccurateThanOneHundredMetres() {
		assertThat(record(NOW, 100.5)).isEqualTo(ReportOutcome.REJECTED_LOW_ACCURACY);
		assertThat(this.store.current(COURIER)).isEmpty();

		assertThat(record(NOW, 100.0)).isEqualTo(ReportOutcome.ACCEPTED);
	}

	@Test
	void rejectsAReadingMeasuredMoreThanThirtySecondsInTheFuture() {
		assertThat(record(NOW.plusSeconds(31), 12.0)).isEqualTo(ReportOutcome.REJECTED_FUTURE_DATED);
		assertThat(this.store.current(COURIER)).isEmpty();

		assertThat(record(NOW.plusSeconds(30), 12.0)).isEqualTo(ReportOutcome.ACCEPTED);
	}

	@Test
	void rejectsAReadingAlreadyOlderThanTwoMinutesOnReceipt() {
		assertThat(record(NOW.minusSeconds(121), 12.0)).isEqualTo(ReportOutcome.REJECTED_STALE);
		assertThat(this.store.current(COURIER)).isEmpty();

		assertThat(record(NOW.minusSeconds(120), 12.0)).isEqualTo(ReportOutcome.ACCEPTED);
	}

	@Test
	void keepsAStoredReadingUntilItIsTwoMinutesOld() {
		record(NOW, 12.0);

		this.clock.advance(Duration.ofSeconds(120));
		assertThat(this.store.current(COURIER)).isPresent();

		this.clock.advance(Duration.ofSeconds(1));
		assertThat(this.store.current(COURIER)).isEmpty();
	}

	@Test
	void forgetsAnExpiredReadingWithoutWaitingForTheSweep() {
		record(NOW, 12.0);
		this.clock.advance(Duration.ofSeconds(121));

		assertThat(this.store.current(COURIER)).isEmpty();
		// The read itself dropped it, so a later read cannot resurrect it by winding time back.
		this.clock.set(NOW);
		assertThat(this.store.current(COURIER)).isEmpty();
	}

	@Test
	void sweepsExpiredReadingsWithoutTouchingUsableOnes() {
		UUID otherCourier = UUID.fromString("33333333-3333-4333-8333-333333333333");
		record(NOW.minusSeconds(110), 12.0);
		this.store.record(new LocationReport(otherCourier, GENERATION, -0.1278, 51.5074, 12.0, NOW));

		this.clock.advance(Duration.ofSeconds(20));
		this.store.forgetExpired();

		this.clock.set(NOW);
		assertThat(this.store.current(COURIER)).isEmpty();
		assertThat(this.store.current(otherCourier)).isPresent();
	}

	@Test
	void supersedesTheSnapshotOfAnEarlierGeneration() {
		record(NOW, 12.0);

		UUID restarted = UUID.fromString("44444444-4444-4444-8444-444444444444");
		ReportOutcome outcome = this.store
			.record(new LocationReport(COURIER, restarted, -0.1278, 51.5074, 12.0, NOW.minusSeconds(30)));

		// Measurement order only orders readings within one Location Sharing Session; a newly
		// started one always describes a Courier the previous session can no longer speak for.
		assertThat(outcome).isEqualTo(ReportOutcome.ACCEPTED);
		assertThat(recordedAt()).isEqualTo(NOW.minusSeconds(30));
	}

	@Test
	void forgetsACourierImmediatelyOnStop() {
		record(NOW, 12.0);

		this.store.forget(COURIER);

		assertThat(this.store.current(COURIER)).isEmpty();
	}

	@Test
	void startsEmptySoARestartLeavesNoCoordinateBehind() {
		record(NOW, 12.0);

		assertThat(new InMemoryLatestLocationStore(this.clock).current(COURIER)).isEmpty();
	}

	private ReportOutcome record(Instant recordedAt, double accuracyMetres) {
		return this.store.record(new LocationReport(COURIER, GENERATION, -0.1278, 51.5074, accuracyMetres, recordedAt));
	}

	private Instant recordedAt() {
		return this.store.current(COURIER).orElseThrow().recordedAt();
	}

	private double accuracyMetres() {
		return this.store.current(COURIER).orElseThrow().accuracyMetres();
	}

}
