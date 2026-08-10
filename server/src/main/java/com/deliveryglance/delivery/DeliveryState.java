package com.deliveryglance.delivery;

/**
 * The Delivery states this Issue can produce. The lifecycle also defines Assigned, In Transit,
 * Delivered and Undeliverable; the Issues that introduce those transitions widen this enum and the
 * matching CHECK constraints together.
 */
enum DeliveryState {

	AWAITING_COURIER,
	CANCELLED

}
