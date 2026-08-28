package com.deliveryglance.eta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Delivery facts an ETA is computed from; delivery supplies the implementation. Like the
 * Recipient projection's own delivery port, the interface lives in the consumer so the dependency
 * runs one way — delivery knows about eta, eta never imports a Delivery type. The lifecycle is
 * mapped to an {@link EtaPhase} on the delivery side, so this module branches on the two phases that
 * carry an ETA without ever seeing the full state machine.
 *
 * <p>What arrives is already the shortlist an estimate needs: the two coordinates a window is drawn
 * between and the Courier whose position supplies the origin. No address label, version or history
 * comes with it, because none of that changes a driving time.
 */
public interface EtaRouteFacts {

	/**
	 * The route facts for one Delivery, or empty when it is not in a phase that carries an ETA — gone,
	 * still awaiting a Courier, or already terminal. An empty answer is the signal to drop any stored
	 * window, so a completed Delivery keeps no estimate.
	 */
	Optional<Route> routeFor(UUID deliveryId);

	/**
	 * Every Delivery currently in an ETA-bearing phase. The sweeper walks this list each tick; at Core
	 * scale it is a short read, and keeping the set on the delivery side means eta never second-guesses
	 * which Deliveries are live.
	 */
	List<UUID> activeDeliveryIds();

	/**
	 * @param courierAccountId the Courier whose Current Location is the routing origin, or
	 * {@code null} when none is assigned — in which case no origin exists and no window can be drawn
	 */
	record Route(EtaPhase phase, double pickupLatitude, double pickupLongitude, double handoffLatitude,
			double handoffLongitude, UUID courierAccountId) {

		GeoPoint pickup() {
			return new GeoPoint(this.pickupLatitude, this.pickupLongitude);
		}

		GeoPoint handoff() {
			return new GeoPoint(this.handoffLatitude, this.handoffLongitude);
		}

		/** The ordered waypoints for this phase, given the courier's current {@code origin}. */
		List<GeoPoint> waypointsFrom(GeoPoint origin) {
			return switch (this.phase) {
				case ASSIGNED -> List.of(origin, pickup(), handoff());
				case IN_TRANSIT -> List.of(origin, handoff());
			};
		}

	}

}
