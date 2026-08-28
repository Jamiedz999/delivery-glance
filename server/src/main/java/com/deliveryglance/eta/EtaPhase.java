package com.deliveryglance.eta;

import java.time.Duration;

/**
 * The two Delivery phases that carry an ETA, each with the shape ADR 05 gives its window. Assigned
 * is provisional — a Courier still has to reach the pickup, so its estimate carries the fixed
 * pickup buffer and a wider window; In Transit is the Courier's own position to the handoff, so it
 * is tighter and unbuffered.
 *
 * <p>This is an eta-local enum on purpose. The module never imports a Delivery's own state type;
 * the delivery side maps its lifecycle onto these two phases when it answers {@link EtaRouteFacts},
 * which keeps the dependency one-directional — delivery knows about eta, eta does not reach back.
 */
public enum EtaPhase {

	/** Courier → pickup + a fixed pickup buffer + pickup → handoff, presented as a ~20-minute window. */
	ASSIGNED(Duration.ofMinutes(5), Duration.ofMinutes(10)),

	/** Current Location → handoff only, presented as a tighter ~10-minute window. */
	IN_TRANSIT(Duration.ZERO, Duration.ofMinutes(5));

	private final Duration buffer;

	private final Duration halfWidth;

	EtaPhase(Duration buffer, Duration halfWidth) {
		this.buffer = buffer;
		this.halfWidth = halfWidth;
	}

	/** The fixed pickup buffer added to travel before the window is drawn. Zero once In Transit. */
	Duration buffer() {
		return this.buffer;
	}

	/** Half the nominal window width, applied either side of the point estimate before rounding. */
	Duration halfWidth() {
		return this.halfWidth;
	}

}
