package com.deliveryglance.eta;

import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalculates every active Delivery's ETA on a timer. This is the "at most once per minute" cadence
 * ADR 05 sets: while a Delivery is In Transit with a usable location the window is refreshed each
 * tick, and a Delivery whose window has aged toward stale gets its chance to recover here. The
 * immediate recompute at Assignment and pickup comes from {@link EtaRecalculation}; this is the
 * steady heartbeat between those.
 *
 * <p>It runs off any request, holds no lock, and recomputes each Delivery independently — one that
 * fails leaves its own window stale and never touches another's, because {@link EtaCalculations}
 * swallows a single Delivery's failure rather than letting it end the pass.
 */
@Component
class EtaSweeper {

	private final EtaCalculations calculations;

	EtaSweeper(EtaCalculations calculations) {
		this.calculations = calculations;
	}

	@Scheduled(fixedDelayString = "${delivery-glance.eta.refresh-interval:PT1M}")
	void refreshActiveWindows() {
		for (UUID deliveryId : this.calculations.activeDeliveryIds()) {
			this.calculations.recalculate(deliveryId);
		}
	}

}
