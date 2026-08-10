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

	boolean canTransitionTo(DeliveryState nextState) {
		return switch (this) {
			case AWAITING_COURIER -> nextState == ASSIGNED || nextState == CANCELLED;
			case ASSIGNED -> nextState == IN_TRANSIT || nextState == CANCELLED;
			case IN_TRANSIT -> nextState == DELIVERED;
			case DELIVERED, CANCELLED -> false;
		};
	}

}
