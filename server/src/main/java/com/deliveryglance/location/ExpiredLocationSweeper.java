package com.deliveryglance.location;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drops coordinates nobody has asked about. Reads already delete an expired snapshot, so this is
 * not what enforces the two-minute boundary — it is what stops a Courier who closed their laptop
 * from leaving a position in memory until the next restart.
 */
@Component
class ExpiredLocationSweeper {

	private final LatestLocationStore store;

	ExpiredLocationSweeper(LatestLocationStore store) {
		this.store = store;
	}

	@Scheduled(fixedDelayString = "PT30S")
	void sweep() {
		this.store.forgetExpired();
	}

}
