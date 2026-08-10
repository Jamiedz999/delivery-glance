package com.deliveryglance.location;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.deliveryglance.MutableClock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the dispatch read meets Location Freshness. Ranking a Courier against a pickup is the one
 * place coordinates leave this module, so the usable limit is enforced on the way out rather than
 * trusted to the caller: past it, dispatch is told there is no position at all.
 */
class LocationSharingDispatchPositionTest {

	private static final UUID COURIER = UUID.fromString("11111111-1111-4111-8111-111111111111");

	private static final UUID GENERATION = UUID.fromString("22222222-2222-4222-8222-222222222222");

	private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

	private final MutableClock clock = new MutableClock(NOW);

	private final LatestLocationStore store = new LatestLocationStore(this.clock);

	// The Location Sharing repository is untouched by this read: a position is held in memory only,
	// so the dispatch read never reaches the database.
	private final LocationSharing sharing = new LocationSharing(null, this.store, this.clock);

	@Test
	void offersTheCoordinatesWhileTheStoredPositionIsStillUsable() {
		record(NOW);
		this.clock.advance(LocationFreshness.USABLE_LIMIT);

		assertThat(this.sharing.positionForDispatch(COURIER))
			.contains(new LocationFacts.DispatchPosition(51.5074, -0.1278));
	}

	@Test
	void withholdsThePositionOnceItIsPastTheUsableLimit() {
		record(NOW);
		this.clock.advance(LocationFreshness.USABLE_LIMIT.plus(Duration.ofSeconds(1)));

		assertThat(this.sharing.positionForDispatch(COURIER)).isEmpty();
	}

	@Test
	void reportsNoPositionForACourierWhoHasNeverSharedOne() {
		assertThat(this.sharing.positionForDispatch(COURIER)).isEmpty();
	}

	private void record(Instant recordedAt) {
		this.store.record(new LocationReport(COURIER, GENERATION, -0.1278, 51.5074, 12.0, recordedAt));
	}

}
