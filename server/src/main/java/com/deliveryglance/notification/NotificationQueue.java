package com.deliveryglance.notification;

import java.util.UUID;

/**
 * The relay's one collaborator: hand a committed transition id to the broker. The interface carries
 * a transition id and nothing else, which is the whole privacy contract of the pipeline in one
 * signature — a volunteered email or phone never travels through the queue, because the only thing
 * that can be enqueued is an opaque identifier the consumer reads the target back from.
 *
 * <p>It is a seam with a real second side: production sends to SQS, and the relay's tests drive a
 * deterministic in-memory double that records what it was asked to enqueue without a broker.
 */
interface NotificationQueue {

	void enqueue(UUID transitionId);

}
