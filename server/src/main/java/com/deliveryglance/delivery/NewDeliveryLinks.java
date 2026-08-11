package com.deliveryglance.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * The Tracking Link every new Delivery gets; trackinglink supplies the implementation.
 *
 * <p>It is one void method because that is the whole of what Delivery creation needs to know. The
 * capability, its derivation and its expiry are entirely trackinglink's business, and a Delivery
 * that could see the token would be a Delivery that could log it.
 */
public interface NewDeliveryLinks {

	/** Runs inside the creating transaction, so a Delivery never exists without its link. */
	void createFor(UUID deliveryId, Instant createdAt);

}
