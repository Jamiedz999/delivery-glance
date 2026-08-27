package com.deliveryglance.notification;

import org.springframework.http.HttpStatus;

/**
 * A notification request the application refused, carrying the HTTP status and stable {@code code}
 * its one handler renders. The messages name a channel and a target, never a queue, a Lambda or SES
 * — a Recipient is told what they may opt into, not how the message is delivered.
 */
class NotificationException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private NotificationException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	/** The volunteered address does not look like the channel it was offered for. */
	static NotificationException invalidTarget(NotificationChannel channel) {
		String what = (channel == NotificationChannel.EMAIL) ? "email address" : "phone number in +country format";
		return new NotificationException(HttpStatus.UNPROCESSABLE_ENTITY, "notification-invalid-target",
				"Enter a valid " + what + " to be notified.");
	}

	/** No queue is configured, so nothing could ever be sent. The feature is off, not the request wrong. */
	static NotificationException unavailable() {
		return new NotificationException(HttpStatus.SERVICE_UNAVAILABLE, "notification-unavailable",
				"Notifications are not available on this deployment.");
	}

	/** The callback presented no valid shared token, so it is not the consumer Lambda. */
	static NotificationException callbackUnauthorized() {
		return new NotificationException(HttpStatus.UNAUTHORIZED, "notification-callback-unauthorized",
				"The notification callback is not authorized.");
	}

	HttpStatus status() {
		return this.status;
	}

	String code() {
		return this.code;
	}

}
