package com.deliveryglance.notification;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import com.deliveryglance.trackinglink.LinkHolderAuthorization;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The opt-in surface a Link Holder's browser uses. It sits under {@code /api/tracking} so it inherits
 * the same grant authorization, {@code no-store} headers and single generic refusal as the snapshot:
 * the capability to read this Delivery is the capability to volunteer a channel for it, and nothing
 * about an Internal Account reaches here.
 *
 * <p>The controller is short because the two questions it composes belong elsewhere — trackinglink
 * decides whether this request may act on a Delivery, and {@link RecipientSubscriptions} decides what
 * a valid opt-in is. There is no third question here to grow a branch.
 */
@RestController
@RequestMapping("/api/tracking/notifications")
class NotificationSubscriptionController {

	private final LinkHolderAuthorization authorization;

	private final RecipientSubscriptions subscriptions;

	NotificationSubscriptionController(LinkHolderAuthorization authorization, RecipientSubscriptions subscriptions) {
		this.authorization = authorization;
		this.subscriptions = subscriptions;
	}

	/**
	 * The opt-in section's state for this grant's Delivery: whether the deployment can notify at all,
	 * and the Recipient's current subscription if they have one. Always 200 — "not available" and "no
	 * subscription yet" are states to render, not errors.
	 */
	@GetMapping
	NotificationViews.OptInState optInState(HttpServletRequest request, HttpServletResponse response) {
		UUID deliveryId = this.authorization.requireAuthorizedDelivery(request, response);
		return this.subscriptions.optInState(deliveryId);
	}

	@PostMapping
	NotificationViews.Subscription subscribe(HttpServletRequest request, HttpServletResponse response,
			@Valid @RequestBody NotificationRequests.Subscribe body) {
		UUID deliveryId = this.authorization.requireAuthorizedDelivery(request, response);
		return this.subscriptions.subscribe(deliveryId, body);
	}

	@DeleteMapping
	ResponseEntity<Void> revoke(HttpServletRequest request, HttpServletResponse response) {
		UUID deliveryId = this.authorization.requireAuthorizedDelivery(request, response);
		this.subscriptions.revoke(deliveryId);
		return ResponseEntity.noContent().build();
	}

}
