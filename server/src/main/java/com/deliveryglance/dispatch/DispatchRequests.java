package com.deliveryglance.dispatch;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

final class DispatchRequests {

	private DispatchRequests() {
	}

	record Assign(@NotNull(message = "is required") UUID courierId,
			@NotNull(message = "is required") @PositiveOrZero(message = "must not be negative") Integer expectedVersion,
			@NotNull(message = "is required") UUID commandId) {
	}

}
