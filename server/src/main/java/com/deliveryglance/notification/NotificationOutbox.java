package com.deliveryglance.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * The one thing the Delivery module asks of notification: record that a transition happened, so a
 * Recipient who opted in can be told. It is called from inside the transition's transaction, right
 * after the transition row is written, so the outbox row and the transition it notifies about commit
 * together or not at all — the notification cannot survive a rolled-back transition, and a committed
 * transition cannot lose its notification to a later crash.
 *
 * <p>It takes a transition id and a time, and nothing about the Delivery's state or the Recipient's
 * channel. Everything else is read from the transition and the subscription already in the database,
 * which is what keeps this module free of the Delivery's types and keeps the caller from having to
 * know which transitions are notify-worthy or whether anyone opted in — the implementation decides
 * both, and a call for a transition that is neither simply writes nothing.
 */
public interface NotificationOutbox {

	void recordTransition(UUID transitionId, Instant occurredAt);

}
