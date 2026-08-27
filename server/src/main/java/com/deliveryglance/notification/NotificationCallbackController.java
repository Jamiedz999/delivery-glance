package com.deliveryglance.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The consumer Lambda's way back into the application, and the only routes not reached by a browser
 * or an Internal Account. The security policy lets them through unauthenticated; they are authorized
 * here, by a shared bearer token compared in constant time. A deployment with no token configured
 * has no Lambda, and every callback is refused — nothing but the Lambda it never configured can
 * record a send.
 *
 * <p>Two steps, because the send between them is the one thing this application cannot make
 * idempotent for the Lambda: {@code begin} decides whether to send and hands back the channel,
 * target and message inputs; {@code sent} records that the provider accepted it. A redelivery calls
 * {@code begin} again and is told the send already happened.
 */
@RestController
@RequestMapping("/api/internal/notifications")
class NotificationCallbackController {

	private final NotificationDispatch dispatch;

	private final NotificationProperties properties;

	NotificationCallbackController(NotificationDispatch dispatch, NotificationProperties properties) {
		this.dispatch = dispatch;
		this.properties = properties;
	}

	@PostMapping("/begin")
	NotificationViews.DispatchDecision begin(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody NotificationRequests.Dispatch body) {
		requireCallbackToken(authorization);
		return this.dispatch.begin(body.transitionId());
	}

	@PostMapping("/sent")
	ResponseEntity<Void> sent(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody NotificationRequests.Dispatch body) {
		requireCallbackToken(authorization);
		this.dispatch.recordSent(body.transitionId());
		return ResponseEntity.noContent().build();
	}

	private void requireCallbackToken(String authorizationHeader) {
		String expected = this.properties.callbackToken();
		if (expected == null || expected.isBlank()) {
			throw NotificationException.callbackUnauthorized();
		}
		String presented = bearerToken(authorizationHeader);
		if (presented == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				presented.getBytes(StandardCharsets.UTF_8))) {
			throw NotificationException.callbackUnauthorized();
		}
	}

	private static String bearerToken(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return null;
		}
		return authorizationHeader.substring(7).strip();
	}

}
