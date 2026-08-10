package com.deliveryglance.delivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a Dispatcher's browser is allowed to see. These are built by the server for one audience; no
 * broader Delivery object leaves the module.
 */
final class DeliveryViews {

	private DeliveryViews() {
	}

	record Address(String addressLabel, double latitude, double longitude) {
	}

	/** Enough to recognise a Delivery in a list and to act on it without reopening it. */
	record Summary(UUID id, String reference, DeliveryState state, int version, String pickupAddressLabel,
			String handoffAddressLabel, Instant createdAt, Instant updatedAt) {
	}

	record Detail(UUID id, String reference, DeliveryState state, int version, Address pickup, Address handoff,
			Instant createdAt, Instant updatedAt, List<Transition> transitions, Assignment assignment) {

		/** The repository reads the Delivery; the active Assignment is attached by the service. */
		Detail withAssignment(Assignment attached) {
			return new Detail(this.id, this.reference, this.state, this.version, this.pickup, this.handoff,
					this.createdAt, this.updatedAt, this.transitions, attached);
		}

	}

	record Assignment(UUID courierId, String courierDisplayName, Instant assignedAt) {
	}

	record Transition(DeliveryState previousState, DeliveryState nextState, String actorDisplayName,
			CancellationReason reasonCode, String reasonNote, Instant occurredAt) {
	}

	record CourierDelivery(UUID id, String reference, DeliveryState state, int version, String pickupAddressLabel,
			String handoffAddressLabel) {
	}

}
