package com.deliveryglance.recipientview;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.deliveryglance.trackinglink.LinkHolderAuthorization;
import com.deliveryglance.trackinglink.LinkHolderAuthorization.HeldGrant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The two routes a Link Holder's browser uses: the one it reads its Delivery through, and the one
 * that tells it when to read again.
 *
 * <p>Both are short because the questions they compose belong to other classes: trackinglink decides
 * whether this request may read a Delivery, {@link RecipientSnapshots} decides what may be said
 * about it, and {@link RecipientStreams} owns the connections. There is no third question for a
 * controller to answer, and no branch here that could accidentally become one.
 *
 * <p>The response headers, the {@code no-store} and the single generic refusal all come from
 * trackinglink's filter and advice, which cover {@code /api/tracking/**} whoever handles it — which
 * is why the stream lives under that prefix too, rather than under a Recipient-specific one that
 * would need its own copy of the security rule and its own copy of the headers.
 */
@RestController
class RecipientTrackingController {

	private final LinkHolderAuthorization authorization;

	private final RecipientSnapshots snapshots;

	private final RecipientStreams streams;

	RecipientTrackingController(LinkHolderAuthorization authorization, RecipientSnapshots snapshots,
			RecipientStreams streams) {
		this.authorization = authorization;
		this.snapshots = snapshots;
		this.streams = streams;
	}

	@GetMapping("/api/tracking/snapshot")
	RecipientViews.Snapshot snapshot(HttpServletRequest request, HttpServletResponse response) {
		UUID deliveryId = this.authorization.requireAuthorizedDelivery(request, response);
		return this.snapshots.of(deliveryId);
	}

	/**
	 * The refresh hints for the Delivery this request's grant authorizes. It sends no Delivery
	 * facts at all — a page still learns everything from {@link #snapshot}, and this only saves it
	 * from having to ask on a timer.
	 *
	 * <p>{@code 503} when the application is at its connection budget. It is not a refusal of the
	 * link, and it does not read as one: the browser stops retrying the stream, the page keeps
	 * whatever snapshot it has and says it is not receiving updates, and a reload gets a full
	 * snapshot as always. Losing live refresh under load is a smaller failure than dropping the
	 * connection budget.
	 */
	@GetMapping("/api/tracking/events")
	ResponseEntity<SseEmitter> events(HttpServletRequest request, HttpServletResponse response) {
		HeldGrant grant = this.authorization.requireHeldGrant(request, response);
		return this.streams.open(grant)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
	}

}
