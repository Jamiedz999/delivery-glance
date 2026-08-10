package com.deliveryglance.dispatch;

import org.springframework.http.HttpStatus;

class DispatchException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private DispatchException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	static DispatchException deliveryNotFound() {
		return new DispatchException(HttpStatus.NOT_FOUND, "delivery-not-found", "No Delivery matches that identifier.");
	}

	static DispatchException deliveryChanged() {
		return new DispatchException(HttpStatus.CONFLICT, "assignment-delivery-changed",
				"The Delivery is no longer Awaiting Courier at the expected version.");
	}

	static DispatchException courierNotEligible() {
		return new DispatchException(HttpStatus.CONFLICT, "courier-not-eligible",
				"The selected Courier is no longer eligible; refresh the recommendation.");
	}

	static DispatchException courierNotRecommended() {
		return new DispatchException(HttpStatus.CONFLICT, "courier-not-recommended",
				"The selected Courier is no longer in the current nearest-three recommendation.");
	}

	static DispatchException assignmentConflict() {
		return new DispatchException(HttpStatus.CONFLICT, "assignment-conflict",
				"Another active Assignment won this race; refresh the Delivery.");
	}

	static DispatchException commandIdReused() {
		return new DispatchException(HttpStatus.CONFLICT, "dispatch-command-id-reused",
				"This command identifier was already used for a different Delivery command.");
	}

	HttpStatus status() {
		return this.status;
	}

	String code() {
		return this.code;
	}

}
