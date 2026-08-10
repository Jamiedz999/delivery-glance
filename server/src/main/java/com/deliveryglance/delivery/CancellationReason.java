package com.deliveryglance.delivery;

/**
 * Why a Dispatcher stopped a Delivery before pickup. {@code OTHER} is only accepted together with a
 * note, so the history never records an unexplained cancellation.
 */
enum CancellationReason {

	NO_LONGER_REQUIRED,
	INVALID_DELIVERY_DETAILS,
	ITEM_UNAVAILABLE_AT_PICKUP,
	OTHER

}
