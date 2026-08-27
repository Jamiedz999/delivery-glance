package com.deliveryglance.notification;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The application's half of the idempotent consumer. The consumer Lambda asks to {@code begin}
 * dispatching a transition, sends only if told to proceed, and confirms the send afterwards. All the
 * exactly-once reasoning lives here, keyed on the transition id, so the Lambda holds no state and a
 * redelivery is decided by what the outbox already records rather than by the broker.
 *
 * <p>Suppression is decided here too, live against the subscription rather than the snapshot: a
 * Recipient who unsubscribed after the transition committed but before it was sent is honoured, which
 * is what makes "unsubscribe from any message" reach even a message already queued.
 */
@Service
class NotificationDispatch {

	private final NotificationRepository repository;

	private final Clock clock;

	NotificationDispatch(NotificationRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	NotificationViews.DispatchDecision begin(UUID transitionId) {
		return this.repository.findDispatch(transitionId).map((row) -> decide(transitionId, row))
			.orElseGet(NotificationViews.DispatchDecision::unknown);
	}

	private NotificationViews.DispatchDecision decide(UUID transitionId, NotificationRepository.DispatchRow row) {
		if (row.sent()) {
			return NotificationViews.DispatchDecision.alreadySent();
		}
		if (row.suppressed()) {
			return NotificationViews.DispatchDecision.suppressed();
		}
		if (!row.subscriptionActive()) {
			// The unsubscribe reached the message in time. Record it as terminal so a redelivery is
			// answered from the row rather than re-deciding, and so the relay stops considering it.
			this.repository.markSuppressed(transitionId, this.clock.instant());
			return NotificationViews.DispatchDecision.suppressed();
		}
		return NotificationViews.DispatchDecision.proceed(row.channel(), row.target(), row.nextState(),
				row.deliveryReference());
	}

	/**
	 * Confirms a send. Idempotent: a second confirmation for the same transition changes nothing, so
	 * a Lambda that sent but failed to record, then retried, does not fail here.
	 */
	@Transactional
	void recordSent(UUID transitionId) {
		this.repository.markSent(transitionId, this.clock.instant());
	}

}
