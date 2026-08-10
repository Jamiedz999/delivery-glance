package com.deliveryglance.delivery;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The Assignment facts Delivery lifecycle commands consume; dispatch supplies the implementation. */
public interface ActiveAssignments {

	Optional<ActiveAssignment> activeForDelivery(UUID deliveryId);

	Optional<ActiveAssignment> activeForCourier(UUID courierId);

	void endForDelivery(UUID deliveryId, Instant endedAt);

	record ActiveAssignment(UUID deliveryId, UUID courierId, String courierDisplayName, Instant assignedAt) {
	}

}
