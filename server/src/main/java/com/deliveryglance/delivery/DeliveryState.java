package com.deliveryglance.delivery;

/**
 * The Delivery states Portfolio Core currently produces. Undeliverable remains deferred.
 */
public enum DeliveryState {

	AWAITING_COURIER,
	ASSIGNED,
	IN_TRANSIT,
	DELIVERED,
	CANCELLED;

	/**
	 * The states across which exactly one Assignment is active. Leaving them — by handoff or by a
	 * pre-pickup Cancel — is what ends that Assignment, so no command has to carry the fact itself.
	 */
	boolean holdsActiveAssignment() {
		return this == ASSIGNED || this == IN_TRANSIT;
	}

	boolean endsAssignmentOnMoveTo(DeliveryState nextState) {
		return holdsActiveAssignment() && !nextState.holdsActiveAssignment();
	}

	boolean canTransitionTo(DeliveryState nextState) {
		return switch (this) {
			case AWAITING_COURIER -> nextState == ASSIGNED || nextState == CANCELLED;
			case ASSIGNED -> nextState == IN_TRANSIT || nextState == CANCELLED;
			case IN_TRANSIT -> nextState == DELIVERED;
			case DELIVERED, CANCELLED -> false;
		};
	}

}
