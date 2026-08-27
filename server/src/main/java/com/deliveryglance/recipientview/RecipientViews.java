package com.deliveryglance.recipientview;

import java.time.Instant;

import com.deliveryglance.delivery.DeliveryState;

/**
 * What a Link Holder's browser is allowed to see, and the only shape this module serialises.
 *
 * <p>Every field is nullable and most of them are null in most states. That is the design: the
 * projection decides what a state may carry, and a field left null is a fact the Recipient is not
 * being told rather than one the client should hide. Nothing here is a Delivery, and there is no
 * field a Delivery identifier, a pickup address or an internal reason could be put in.
 */
final class RecipientViews {

	private RecipientViews() {
	}

	/**
	 * @param reference the Recipient-facing Delivery Reference, present in every state — it is what
	 * a Recipient quotes when they contact the Delivery Team
	 * @param handoffAddressLabel the full Handoff Address, which ADR 05 makes Recipient-facing; null
	 * once Cancelled, because it is the field that says where somebody lives and there is nothing
	 * left to deliver there
	 * @param courierDisplayName the limited Courier Display Name, present only while a Courier is
	 * actually carrying the Delivery
	 * @param map present only In Transit. Its absence is what withdraws Recipient-facing location on
	 * handoff, so no client rule has to remember to.
	 * @param completedAt the actual handoff time, or the cancellation time
	 * @param deliveryTeamContact the Delivery Team's configured contact, offered only once there is
	 * nothing left to track
	 * @param proofOnFile whether the Delivery was confirmed with proof on file. Null in every state
	 * but Delivered — like every other field here, absence is the Recipient not being told — so
	 * proof presence is disclosed only once there is a completed handoff to attach it to, and never
	 * the image itself. This is the whole of what the privacy decision lets a Recipient learn about
	 * proof.
	 */
	record Snapshot(String reference, DeliveryState state, String handoffAddressLabel, String courierDisplayName,
			MapView map, Instant completedAt, String deliveryTeamContact, Boolean proofOnFile) {
	}

	/**
	 * @param courier null whenever no usable position exists — not sharing, stopped, or past the
	 * point where coordinates are kept. The handoff marker stays either way, so an unavailable
	 * Courier leaves a map with a destination on it rather than no map.
	 */
	record MapView(Place handoff, CourierPosition courier) {
	}

	record Place(double latitude, double longitude) {
	}

	/**
	 * @param recordedAt when the device measured the position. The browser ages this itself, which
	 * is what lets the marker expire on a page nobody is refreshing.
	 */
	record CourierPosition(double latitude, double longitude, double accuracyMetres, Instant recordedAt) {
	}

}
