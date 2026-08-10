package com.deliveryglance.delivery;

import java.util.Map;

/**
 * A command that was refused because the Delivery is not in the state the caller assumed. The
 * conflict is reported with the current facts instead of overwriting them.
 */
class DeliveryConflictException extends RuntimeException {

	private final String code;

	private final Map<String, Object> currentFacts;

	private DeliveryConflictException(String code, String message, Map<String, Object> currentFacts) {
		super(message);
		this.code = code;
		this.currentFacts = currentFacts;
	}

	static DeliveryConflictException referenceTaken(String reference) {
		return new DeliveryConflictException("delivery-reference-taken",
				"Another Delivery already uses the Delivery Reference '%s'.".formatted(reference), Map.of());
	}

	static DeliveryConflictException versionConflict(DeliveryState currentState, int currentVersion) {
		return new DeliveryConflictException("delivery-version-conflict",
				"This Delivery changed since it was loaded; reload it and try again.",
				Map.of("currentState", currentState, "currentVersion", currentVersion));
	}

	static DeliveryConflictException invalidTransition(DeliveryState currentState, DeliveryState requestedState) {
		return new DeliveryConflictException("delivery-invalid-transition",
				"A Delivery in %s cannot move to %s.".formatted(currentState, requestedState),
				Map.of("currentState", currentState));
	}

	static DeliveryConflictException commandIdReused() {
		return new DeliveryConflictException("delivery-command-id-reused",
				"This command identifier was already used for a different Delivery.", Map.of());
	}

	static DeliveryConflictException notAssignedToCourier() {
		return new DeliveryConflictException("delivery-not-assigned-to-courier",
				"Only the Courier with this active Assignment can progress the Delivery.", Map.of());
	}

	String code() {
		return this.code;
	}

	Map<String, Object> currentFacts() {
		return this.currentFacts;
	}

}
