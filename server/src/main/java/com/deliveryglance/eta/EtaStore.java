package com.deliveryglance.eta;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The one current ETA Window per Delivery, as storage. {@link EtaRepository} is the durable
 * implementation; the interface exists so {@link EtaCalculations}' decision rules — keep a stale
 * window, withdraw a lost one, hold a jittering one still — can be tested against an in-memory double
 * with a fake clock, without a database standing in for logic that is entirely about time and state.
 */
interface EtaStore {

	Optional<StoredEta> find(UUID deliveryId);

	/**
	 * Stores the window for a Delivery, replacing any current one. A recalculation that did not move
	 * the window passes the endpoints it already holds with a fresh {@code calculatedAt}, so the window
	 * stays put while its freshness advances.
	 */
	void upsert(UUID deliveryId, EtaWindow window, Instant calculatedAt);

	/** Withdraws the current window for a Delivery. A no-op when none is stored. */
	void delete(UUID deliveryId);

	record StoredEta(EtaWindow window, Instant calculatedAt) {
	}

}
