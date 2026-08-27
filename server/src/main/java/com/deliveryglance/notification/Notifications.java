package com.deliveryglance.notification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Writes the outbox row for a transition. It holds no transaction of its own: it runs inside the
 * Delivery command's transaction, which is the entire point — the row it writes is only ever seen if
 * that command commits.
 *
 * <p>There is no policy here beyond delegating to the one repository statement that carries it. The
 * decision of whether a transition is notify-worthy, and whether a Recipient opted in, is made by the
 * database in that statement, so this class stays as thin as its interface promises.
 */
@Service
class Notifications implements NotificationOutbox {

	private final NotificationRepository repository;

	Notifications(NotificationRepository repository) {
		this.repository = repository;
	}

	@Override
	public void recordTransition(UUID transitionId, Instant occurredAt) {
		this.repository.recordTransition(UUID.randomUUID(), transitionId, occurredAt);
	}

}
