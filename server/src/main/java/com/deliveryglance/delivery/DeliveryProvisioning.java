package com.deliveryglance.delivery;

/**
 * Creating a Delivery from somewhere other than the Dispatcher's HTTP form.
 *
 * <p>It exists so that the demo reset can put its fictional Deliveries back through the same use
 * case a Dispatcher's form reaches, rather than writing rows that merely resemble what that use case
 * produces. The difference is the whole point: a Delivery created this way gets its first transition
 * and its Tracking Link from the one transaction that always makes them, so demo data cannot drift
 * into a shape the product would never create.
 *
 * <p>There is no state parameter, and no way to ask for one. A Delivery starts Awaiting Courier or
 * it is not a Delivery this module made; every later state is reached by the guarded transitions.
 * Whoever calls this must be acting as an Internal Account, because the transition it writes records
 * who made the Delivery.
 */
public interface DeliveryProvisioning {

	void createAwaitingCourier(NewDelivery delivery);

	record NewDelivery(String reference, NewAddress pickup, NewAddress handoff) {
	}

	record NewAddress(String addressLabel, double latitude, double longitude) {
	}

}
