package com.deliveryglance.trackinglink;

import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Copy Tracking Link, the Dispatcher's one link command in Core.
 *
 * <p>It sits under the Delivery's URL because that is what it is about, but the behaviour belongs to
 * this module and the delivery module never sees a token. Authorization is the Dispatcher role rule
 * already in the security policy.
 *
 * <p>{@code POST} rather than {@code GET} although it returns something: it records who copied and
 * when, so it is not safe in the HTTP sense and must not be prefetched or retried by a proxy.
 */
@RestController
class TrackingLinkController {

	private final TrackingLinks links;

	TrackingLinkController(TrackingLinks links) {
		this.links = links;
	}

	@PostMapping("/api/deliveries/{id}/tracking-link/copy")
	TrackingLinkViews.CopiedLink copy(@PathVariable UUID id, HttpServletResponse response) {
		// This is the only response in the application whose body contains a raw capability. It is
		// no-store and no-referrer for the same reasons the Recipient's own responses are.
		TrackingResponseHeaders.apply(response);
		return this.links.copyFor(id);
	}

}
