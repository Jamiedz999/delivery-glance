package com.deliveryglance.recipientview;

import java.util.UUID;

import com.deliveryglance.eta.EtaProjection;
import com.deliveryglance.location.LocationFacts;
import com.deliveryglance.proof.ProofPresence;
import com.deliveryglance.recipientview.RecipientDeliveryFacts.RecipientDelivery;
import com.deliveryglance.trackinglink.UnavailableLinkException;

import org.springframework.stereotype.Service;

/**
 * The Recipient projection: one Delivery, reduced to what its state makes public.
 *
 * <p>The switch below is the whole rule. Every state builds the response from scratch rather than
 * starting from a full picture and removing fields, so a fact reaches a Recipient only because a
 * branch put it there. That is what makes "Delivered withdraws Recipient-facing location" true by
 * construction: no branch after In Transit ever asks location anything.
 *
 * <p>Nothing here consults the grant. Whether this request may read this Delivery was settled
 * before the identifier arrived; what may be said about it is settled here, and the two questions
 * stay separate.
 */
@Service
class RecipientSnapshots {

	private final RecipientDeliveryFacts deliveries;

	private final LocationFacts locations;

	private final RecipientViewProperties properties;

	private final ProofPresence proofPresence;

	private final EtaProjection eta;

	RecipientSnapshots(RecipientDeliveryFacts deliveries, LocationFacts locations, RecipientViewProperties properties,
			ProofPresence proofPresence, EtaProjection eta) {
		this.deliveries = deliveries;
		this.locations = locations;
		this.properties = properties;
		this.proofPresence = proofPresence;
		this.eta = eta;
	}

	/**
	 * @throws UnavailableLinkException if the Delivery is gone. Authorization already found it, so
	 * this is a Delivery that disappeared underneath a valid grant rather than an ordinary refusal —
	 * and the Link Holder still gets the one answer every tracking failure produces.
	 */
	RecipientViews.Snapshot of(UUID deliveryId) {
		RecipientDelivery delivery = this.deliveries.recipientFactsFor(deliveryId)
			.orElseThrow(UnavailableLinkException::new);

		return switch (delivery.state()) {
			case AWAITING_COURIER -> awaitingCourier(delivery);
			case ASSIGNED -> assigned(deliveryId, delivery);
			case IN_TRANSIT -> inTransit(deliveryId, delivery);
			case DELIVERED -> delivered(deliveryId, delivery);
			case CANCELLED -> cancelled(delivery);
		};
	}

	/** No Courier has been arranged, so there is no Courier and no location to be honest about. */
	private RecipientViews.Snapshot awaitingCourier(RecipientDelivery delivery) {
		return new RecipientViews.Snapshot(delivery.reference(), delivery.state(), delivery.handoffAddressLabel(),
				null, null, null, null, null, null);
	}

	/**
	 * A Courier is on the way to pickup. The Display Name and a provisional ETA Window appear; the map
	 * does not, because a Courier heading to a pickup address is not information about this Delivery's
	 * journey and showing it would put the pickup address on screen by inference.
	 */
	private RecipientViews.Snapshot assigned(UUID deliveryId, RecipientDelivery delivery) {
		return new RecipientViews.Snapshot(delivery.reference(), delivery.state(), delivery.handoffAddressLabel(),
				delivery.courierDisplayName(), null, null, null, null, etaFor(deliveryId));
	}

	/** The only state that carries coordinates, and the only one that asks location anything. */
	private RecipientViews.Snapshot inTransit(UUID deliveryId, RecipientDelivery delivery) {
		return new RecipientViews.Snapshot(delivery.reference(), delivery.state(), delivery.handoffAddressLabel(),
				delivery.courierDisplayName(), mapFor(delivery), null, null, null, etaFor(deliveryId));
	}

	/**
	 * The Delivery arrived. Reference, Handoff Address and the actual handoff time remain, because
	 * they are what a Recipient checking an old link needs; Courier identity and every trace of
	 * location are gone the moment the state changed, with no sweep to wait for.
	 */
	private RecipientViews.Snapshot delivered(UUID deliveryId, RecipientDelivery delivery) {
		return new RecipientViews.Snapshot(delivery.reference(), delivery.state(), delivery.handoffAddressLabel(),
				null, null, delivery.completedAt(), null, this.proofPresence.hasProofOnFile(deliveryId), null);
	}

	/**
	 * The Reference, a generic outcome, its time, and who to ask. The Reference stays because the
	 * same page hands out a Delivery Team Contact, and a page that tells somebody to phone the team
	 * while withholding the only identifier that call can start from is not being careful, it is
	 * being useless. The Handoff Address goes, because that is the field that says where a person
	 * lives — so a link left in a group chat outlives the Delivery without describing anybody's
	 * home. ADR 06 records the split.
	 */
	private RecipientViews.Snapshot cancelled(RecipientDelivery delivery) {
		return new RecipientViews.Snapshot(delivery.reference(), delivery.state(), null, null, null,
				delivery.completedAt(), configuredContact(), null, null);
	}

	/**
	 * The current window for a Delivery, or null when there is none. A null in an ETA-bearing state is
	 * the honest "ETA temporarily unavailable": the last estimate failed or the Courier's location is
	 * not usable, and the page says so rather than showing a stale guess as fact. The read is of the
	 * stored projection only — a Recipient's page load never waits on the travel-time provider.
	 */
	private RecipientViews.EtaView etaFor(UUID deliveryId) {
		return this.eta.currentEta(deliveryId)
			.map((window) -> new RecipientViews.EtaView(window.windowStart(), window.windowEnd(),
					window.calculatedAt()))
			.orElse(null);
	}

	private RecipientViews.MapView mapFor(RecipientDelivery delivery) {
		// The handoff marker is unconditional: a map with a destination and no Courier is the honest
		// picture of "we do not know where they are", and an empty map would be a broken one.
		RecipientViews.Place handoff = new RecipientViews.Place(delivery.handoffLatitude(),
				delivery.handoffLongitude());
		if (delivery.courierAccountId() == null) {
			return new RecipientViews.MapView(handoff, null);
		}
		return new RecipientViews.MapView(handoff, this.locations.positionForTracking(delivery.courierAccountId())
			.map((position) -> new RecipientViews.CourierPosition(position.latitude(), position.longitude(),
					position.accuracyMetres(), position.recordedAt()))
			.orElse(null));
	}

	private String configuredContact() {
		String contact = this.properties.deliveryTeamContact();
		return (contact == null || contact.isBlank()) ? null : contact.strip();
	}

}
