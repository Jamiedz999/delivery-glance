package com.deliveryglance.recipientview;

import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.delivery.DeliveryState;

/**
 * Which Delivery a Courier is currently carrying; dispatch supplies the implementation, because the
 * Assignment is the only thing that joins a Courier to a Delivery.
 *
 * <p>It is a second, deliberately tiny port rather than a method on {@link RecipientDeliveryFacts},
 * and the reason is a constructor cycle: delivery calls {@link RecipientViewUpdates} when a
 * transition commits, so the class that implements {@code RecipientViewUpdates} cannot depend on
 * the delivery service that implements {@code RecipientDeliveryFacts}. Dispatch depends on nothing
 * in this package, so answering from there closes no loop — the same arrangement, and the same
 * reason, as {@code ActiveAssignments}.
 *
 * <p>It reports the Delivery's state rather than filtering by it. Whether a location change is
 * visible to a Recipient at all is this package's rule, and moving it into another module's SQL
 * would put it somewhere nobody reading the projection would think to look.
 */
public interface CarriedDeliveries {

	Optional<CarriedDelivery> carriedBy(UUID courierAccountId);

	record CarriedDelivery(UUID deliveryId, DeliveryState state) {
	}

}
