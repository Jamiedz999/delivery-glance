package com.deliveryglance.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The opt-in a Recipient volunteers from the tracking page, and its revoke. Every method takes the
 * Delivery the caller's grant already authorized — deciding whether this request may touch this
 * Delivery is the controller's job with trackinglink, and is settled before an id reaches here.
 *
 * <p>The target is validated against its channel here rather than by an annotation, because the rule
 * depends on the channel: an email and an E.164 phone are two shapes, and the wrong one is a Recipient
 * mistake to report clearly, not a malformed request to reject generically.
 */
@Service
class RecipientSubscriptions {

	// Deliberately liberal: one @, no spaces, a dot in the domain. The channel is a courtesy the
	// Recipient chose, so the gate rejects the obviously wrong without pretending to certify an
	// address SES itself is the real judge of.
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	// E.164: a leading + and up to fifteen digits. SMS through SNS requires this shape, so the gate
	// is the same one the provider applies rather than a looser guess.
	private static final Pattern PHONE = Pattern.compile("^\\+[1-9]\\d{6,14}$");

	private final NotificationRepository repository;

	private final NotificationProperties properties;

	private final Clock clock;

	RecipientSubscriptions(NotificationRepository repository, NotificationProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	NotificationViews.Subscription subscribe(UUID deliveryId, NotificationRequests.Subscribe request) {
		// Refuse to store a channel a deployment could never send to. A subscription that would
		// silently never notify is a worse answer than telling the page the feature is off.
		if (!this.properties.isConfigured()) {
			throw NotificationException.unavailable();
		}
		String target = request.target().strip();
		if (!isValidTarget(request.channel(), target)) {
			throw NotificationException.invalidTarget(request.channel());
		}
		Instant now = this.clock.instant();
		this.repository.upsertSubscription(UUID.randomUUID(), deliveryId, request.channel(), target, now);
		return new NotificationViews.Subscription(request.channel().name(), target, true);
	}

	@Transactional
	void revoke(UUID deliveryId) {
		this.repository.revokeSubscription(deliveryId, this.clock.instant());
	}

	@Transactional(readOnly = true)
	NotificationViews.OptInState optInState(UUID deliveryId) {
		NotificationViews.Subscription subscription = this.repository.findSubscription(deliveryId)
			.map((row) -> new NotificationViews.Subscription(row.channel().name(), row.target(), row.active()))
			.orElse(null);
		return new NotificationViews.OptInState(this.properties.isConfigured(), subscription);
	}

	private static boolean isValidTarget(NotificationChannel channel, String target) {
		return switch (channel) {
			case EMAIL -> EMAIL.matcher(target).matches();
			case SMS -> PHONE.matcher(target).matches();
		};
	}

}
