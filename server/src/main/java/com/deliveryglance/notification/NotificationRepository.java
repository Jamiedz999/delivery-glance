package com.deliveryglance.notification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit SQL for the notification module's two tables: the opt-in subscription a Recipient
 * volunteers, and the outbox that decouples the send from the transition. Every statement is
 * written out here so the durable shape is readable in one place.
 *
 * <p>The outbox write is an {@code INSERT ... SELECT} joined to the subscription, which is where the
 * central rules of the pipeline live in the database rather than in a branch: a row is created only
 * for a notify-worthy transition ({@code next_state} filter) and only when an active subscription
 * exists (the join). Neither can be forgotten by a caller.
 */
@Repository
class NotificationRepository {

	private final JdbcClient jdbcClient;

	NotificationRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	// --- Subscription -----------------------------------------------------------------------------

	/**
	 * Records or replaces the one subscription for a Delivery. A second opt-in — a changed channel,
	 * or a re-subscribe after a revoke — updates the same row and clears any revocation, so a
	 * Delivery never accumulates competing channels and a re-opt-in is not a new consent to reconcile
	 * against the old one.
	 */
	void upsertSubscription(UUID id, UUID deliveryId, NotificationChannel channel, String target, Instant now) {
		this.jdbcClient.sql("""
				INSERT INTO recipient_notification_subscription
					(id, delivery_id, channel, target, consented_at, revoked_at, created_at, updated_at)
				VALUES (:id, :deliveryId, :channel, :target, :now, NULL, :now, :now)
				ON CONFLICT (delivery_id) DO UPDATE
					SET channel = :channel, target = :target, consented_at = :now, revoked_at = NULL, updated_at = :now
				""")
			.param("id", id)
			.param("deliveryId", deliveryId)
			.param("channel", channel.name())
			.param("target", target)
			.param("now", offset(now))
			.update();
	}

	int revokeSubscription(UUID deliveryId, Instant now) {
		return this.jdbcClient.sql("""
				UPDATE recipient_notification_subscription
				SET revoked_at = :now, updated_at = :now
				WHERE delivery_id = :deliveryId AND revoked_at IS NULL
				""")
			.param("deliveryId", deliveryId)
			.param("now", offset(now))
			.update();
	}

	Optional<SubscriptionView> findSubscription(UUID deliveryId) {
		return this.jdbcClient.sql("""
				SELECT channel, target, (revoked_at IS NULL) AS active
				FROM recipient_notification_subscription
				WHERE delivery_id = :deliveryId
				""")
			.param("deliveryId", deliveryId)
			.query((rs, rowNumber) -> new SubscriptionView(NotificationChannel.valueOf(rs.getString("channel")),
					rs.getString("target"), rs.getBoolean("active")))
			.optional();
	}

	// --- Outbox -----------------------------------------------------------------------------------

	/**
	 * Writes the outbox row for a transition, if one is due. The select supplies every column, so a
	 * transition that is not notify-worthy, or whose Delivery has no active subscription, produces no
	 * row and the method reports it wrote nothing. {@code ON CONFLICT DO NOTHING} makes a repeated
	 * call for the same transition a no-op rather than a duplicate.
	 *
	 * @return the number of rows written: 1 when a message is due, 0 otherwise.
	 */
	int recordTransition(UUID id, UUID transitionId, Instant now) {
		return this.jdbcClient.sql("""
				INSERT INTO notification_outbox
					(id, transition_id, delivery_id, next_state, delivery_reference, channel, target, created_at)
				SELECT :id, t.id, t.delivery_id, t.next_state, d.reference, s.channel, s.target, :now
				FROM delivery_transition t
				JOIN delivery d ON d.id = t.delivery_id
				JOIN recipient_notification_subscription s
					ON s.delivery_id = t.delivery_id AND s.revoked_at IS NULL
				WHERE t.id = :transitionId
					AND t.next_state IN ('ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')
				ON CONFLICT (transition_id) DO NOTHING
				""")
			.param("id", id)
			.param("transitionId", transitionId)
			.param("now", offset(now))
			.update();
	}

	/** The transition ids of outbox rows still to relay, oldest first. */
	List<UUID> findUnpublished(int limit) {
		return this.jdbcClient.sql("""
				SELECT transition_id
				FROM notification_outbox
				WHERE published_at IS NULL AND sent_at IS NULL AND suppressed_at IS NULL
				ORDER BY created_at
				LIMIT :limit
				""")
			.param("limit", limit)
			.query(UUID.class)
			.list();
	}

	void markPublished(UUID transitionId, Instant now) {
		this.jdbcClient.sql("UPDATE notification_outbox SET published_at = :now WHERE transition_id = :transitionId")
			.param("transitionId", transitionId)
			.param("now", offset(now))
			.update();
	}

	/**
	 * The outbox row a consumer is asking to dispatch, with the two facts that decide whether it may:
	 * whether a send is already recorded, and whether the subscription is still active. The
	 * subscription is read live rather than from the snapshot, so a revoke that landed after the
	 * transition is seen here and stops the send.
	 */
	Optional<DispatchRow> findDispatch(UUID transitionId) {
		return this.jdbcClient.sql("""
				SELECT o.channel, o.target, o.next_state, o.delivery_reference,
					(o.sent_at IS NOT NULL) AS sent,
					(o.suppressed_at IS NOT NULL) AS suppressed,
					(s.id IS NOT NULL AND s.revoked_at IS NULL) AS subscription_active
				FROM notification_outbox o
				LEFT JOIN recipient_notification_subscription s ON s.delivery_id = o.delivery_id
				WHERE o.transition_id = :transitionId
				""")
			.param("transitionId", transitionId)
			.query((rs, rowNumber) -> new DispatchRow(NotificationChannel.valueOf(rs.getString("channel")),
					rs.getString("target"), rs.getString("next_state"), rs.getString("delivery_reference"),
					rs.getBoolean("sent"), rs.getBoolean("suppressed"), rs.getBoolean("subscription_active")))
			.optional();
	}

	void markSuppressed(UUID transitionId, Instant now) {
		this.jdbcClient.sql("""
				UPDATE notification_outbox SET suppressed_at = :now
				WHERE transition_id = :transitionId AND sent_at IS NULL AND suppressed_at IS NULL
				""")
			.param("transitionId", transitionId)
			.param("now", offset(now))
			.update();
	}

	int markSent(UUID transitionId, Instant now) {
		return this.jdbcClient.sql("""
				UPDATE notification_outbox SET sent_at = :now
				WHERE transition_id = :transitionId AND sent_at IS NULL
				""")
			.param("transitionId", transitionId)
			.param("now", offset(now))
			.update();
	}

	private static OffsetDateTime offset(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	record SubscriptionView(NotificationChannel channel, String target, boolean active) {
	}

	record DispatchRow(NotificationChannel channel, String target, String nextState, String deliveryReference,
			boolean sent, boolean suppressed, boolean subscriptionActive) {
	}

}
