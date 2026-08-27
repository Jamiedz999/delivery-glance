package com.deliveryglance.notification;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves committed outbox rows to the queue. It runs on a timer rather than inline with the
 * transition, which is the decoupling the whole feature rests on: a slow or failing broker delays a
 * notification, never a Dispatcher or Courier command.
 *
 * <p>It is at-least-once by design. A row is enqueued and then marked published in two steps, so a
 * crash between them re-enqueues it on the next pass; exactly-once is the consumer's job, keyed on
 * the transition id. Ordering the batch would gain nothing a redelivery does not already cover.
 *
 * <p>The queue bean exists only on a configured deployment, so the relay reaches for it through an
 * {@link ObjectProvider} and does nothing when it is absent — an unconfigured deployment accumulates
 * outbox rows and sends none, which is Core running unchanged.
 */
@Component
class NotificationRelay {

	private final NotificationRepository repository;

	private final NotificationProperties properties;

	private final ObjectProvider<NotificationQueue> queue;

	private final Clock clock;

	NotificationRelay(NotificationRepository repository, NotificationProperties properties,
			ObjectProvider<NotificationQueue> queue, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.queue = queue;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${delivery-glance.notification.relay-interval:PT5S}")
	void publishPending() {
		if (!this.properties.isConfigured()) {
			return;
		}
		NotificationQueue target = this.queue.getIfAvailable();
		if (target != null) {
			publishBatch(target);
		}
	}

	/**
	 * Publishes one bounded batch of unpublished rows to the given queue, marking each published only
	 * after it is enqueued. Returns how many it relayed, so a caller — the scheduler above, or a test
	 * — can tell a busy pass from an idle one.
	 */
	int publishBatch(NotificationQueue target) {
		List<UUID> due = this.repository.findUnpublished(this.properties.relayBatchSize());
		for (UUID transitionId : due) {
			target.enqueue(transitionId);
			this.repository.markPublished(transitionId, this.clock.instant());
		}
		return due.size();
	}

}
