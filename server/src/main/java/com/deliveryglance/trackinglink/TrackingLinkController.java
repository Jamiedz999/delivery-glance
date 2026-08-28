package com.deliveryglance.trackinglink;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Dispatcher's Tracking Link commands in Core: Copy and Revocation.
 *
 * <p>They sit under the Delivery's URL because that is what they are about, but the behaviour
 * belongs to this module and the delivery module never sees a token. Authorization is the Dispatcher
 * role rule already in the security policy.
 *
 * <p>Both are {@code POST} rather than {@code GET}: Copy records who copied and when, and Revocation
 * changes the link's lifecycle, so neither is safe in the HTTP sense and must not be prefetched or
 * retried by a proxy.
 */
@RestController
class TrackingLinkController {

	private final TrackingLinks links;

	TrackingLinkController(TrackingLinks links) {
		this.links = links;
	}

	@PostMapping("/api/deliveries/{id}/tracking-link/copy")
	TrackingLinkViews.CopiedLink copy(@PathVariable UUID id) {
		return this.links.copyFor(id);
	}

	@PostMapping("/api/deliveries/{id}/tracking-link/revoke")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revoke(@PathVariable UUID id, @Valid @RequestBody TrackingLinkRequests.Revoke request) {
		this.links.revoke(id, request.reason(), request.note());
	}

}
