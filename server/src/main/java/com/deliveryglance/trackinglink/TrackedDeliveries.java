package com.deliveryglance.trackinglink;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The Delivery facts a Tracking Link needs; delivery supplies the implementation.
 *
 * <p>This is the second interface between the same pair of modules, and deliberately so. Delivery
 * creation calls into trackinglink through {@code NewDeliveryLinks}, and a link read calls back the
 * other way for the two facts below. Implementing both on the same delivery-side bean would make a
 * constructor cycle, so the implementation of this one reads the Delivery tables and depends on
 * nothing in trackinglink — the same arrangement dispatch already uses for {@code ActiveAssignments}.
 */
public interface TrackedDeliveries {

	Optional<TrackedDelivery> find(UUID deliveryId);

	/**
	 * @param reference the Recipient-facing Delivery Reference
	 * @param terminalAt when the Delivery reached a terminal state, or {@code null} if it has not.
	 * The link's remaining grace period is derived from this, so it is read fresh on every access
	 * rather than copied into the link when the Delivery ends.
	 */
	record TrackedDelivery(UUID deliveryId, String reference, Instant terminalAt) {
	}

}
