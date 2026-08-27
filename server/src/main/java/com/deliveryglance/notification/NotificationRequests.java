package com.deliveryglance.notification;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The bodies the notification API accepts: what a Recipient volunteers, and what the consumer
 * Lambda names on its way back. None carries anything about the Delivery's internals.
 */
final class NotificationRequests {

	private NotificationRequests() {
	}

	/**
	 * A Recipient's opt-in: the channel they choose and the address to use. The target's shape is
	 * checked against the channel by the service, not here, because "looks like an email" and "looks
	 * like a phone number" are two different rules selected by the channel field.
	 */
	record Subscribe(
			@NotNull(message = "is required") NotificationChannel channel,

			@NotBlank(message = "is required") @Size(max = 320, message = "is too long") String target) {
	}

	/** What a callback names: the transition it is dispatching or confirming a send for. */
	record Dispatch(@NotNull(message = "is required") UUID transitionId) {
	}

}
