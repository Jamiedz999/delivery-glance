package com.deliveryglance.recipientview;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.deliveryglance.trackinglink.LinkHolderAuthorization;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one route a Link Holder's browser reads its Delivery through.
 *
 * <p>It is two lines because the two questions it composes belong to other classes: trackinglink
 * decides whether this request may read a Delivery, and {@link RecipientSnapshots} decides what may
 * be said about it. There is no third question for a controller to answer, and no branch here that
 * could accidentally become one.
 *
 * <p>The response headers, the {@code no-store} and the single generic refusal all come from
 * trackinglink's filter and advice, which cover {@code /api/tracking/**} whoever handles it.
 */
@RestController
class RecipientTrackingController {

	private final LinkHolderAuthorization authorization;

	private final RecipientSnapshots snapshots;

	RecipientTrackingController(LinkHolderAuthorization authorization, RecipientSnapshots snapshots) {
		this.authorization = authorization;
		this.snapshots = snapshots;
	}

	@GetMapping("/api/tracking/snapshot")
	RecipientViews.Snapshot snapshot(HttpServletRequest request, HttpServletResponse response) {
		UUID deliveryId = this.authorization.requireAuthorizedDelivery(request, response);
		return this.snapshots.of(deliveryId);
	}

}
