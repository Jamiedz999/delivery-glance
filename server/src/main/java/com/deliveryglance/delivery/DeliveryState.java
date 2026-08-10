package com.deliveryglance.delivery;

/**
 * The Delivery states Portfolio Core currently produces. Undeliverable remains deferred.
 */
public enum DeliveryState {

	AWAITING_COURIER,
	ASSIGNED,
	IN_TRANSIT,
	DELIVERED,
	CANCELLED

}
