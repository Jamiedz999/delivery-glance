package com.deliveryglance.delivery;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The small Delivery application interface used to calculate and apply Direct Assignment. */
public interface DeliveryAssignmentOperations {

	Optional<AssignmentTarget> assignmentTarget(UUID deliveryId);

	Optional<AssignmentTarget> lockAssignmentTarget(UUID deliveryId);

	Optional<UUID> deliveryIdForCommand(UUID commandId);

	void transitionToAssigned(UUID deliveryId, int expectedVersion, UUID commandId, Instant occurredAt);

	record AssignmentTarget(UUID deliveryId, DeliveryState state, int version, double pickupLatitude,
			double pickupLongitude) {
	}

}
