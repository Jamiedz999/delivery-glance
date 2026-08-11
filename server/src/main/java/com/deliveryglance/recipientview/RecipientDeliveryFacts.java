package com.deliveryglance.recipientview;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.delivery.DeliveryState;

/**
 * The Delivery facts a Recipient projection is built from; delivery supplies the implementation.
 *
 * <p>It is a read of one Delivery and deliberately not the Delivery itself. What arrives here is
 * already the shortlist — no pickup address, no version, no transition history, no internal account
 * identity — so the projection below is choosing between facts a Recipient may see rather than
 * filtering a domain object down and hoping nothing was missed.
 *
 * <p>The Courier is on this record rather than read separately because "who is carrying this
 * Delivery right now" is an Assignment question, and delivery is where the Assignment is already
 * joined to the Delivery.
 */
public interface RecipientDeliveryFacts {

	Optional<RecipientDelivery> recipientFactsFor(UUID deliveryId);

	/**
	 * @param courierAccountId the assigned Courier, or {@code null} when none is. It is here only so
	 * the projection can ask location where that Courier is; it never reaches a response.
	 * @param completedAt when the Delivery reached a terminal state, or {@code null} if it has not
	 */
	record RecipientDelivery(String reference, DeliveryState state, String handoffAddressLabel,
			double handoffLatitude, double handoffLongitude, UUID courierAccountId, String courierDisplayName,
			Instant completedAt) {

		/** The Delivery read and the Assignment read are separate; this joins them. */
		public RecipientDelivery withCourier(UUID accountId, String displayName) {
			return new RecipientDelivery(this.reference, this.state, this.handoffAddressLabel, this.handoffLatitude,
					this.handoffLongitude, accountId, displayName, this.completedAt);
		}

	}

}
