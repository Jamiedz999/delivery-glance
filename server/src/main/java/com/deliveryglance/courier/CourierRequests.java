package com.deliveryglance.courier;

import jakarta.validation.constraints.NotNull;

/**
 * The commands a Courier can send about themselves.
 */
final class CourierRequests {

	private CourierRequests() {
	}

	/** Boxed so a missing field is a validation message rather than a silent {@code false}. */
	record Duty(@NotNull(message = "is required") Boolean onDuty) {
	}

}
