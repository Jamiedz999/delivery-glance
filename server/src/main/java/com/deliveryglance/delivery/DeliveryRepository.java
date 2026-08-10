package com.deliveryglance.delivery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.identityaccess.CurrentActor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit SQL for the two Delivery tables. Every statement is written out here rather than
 * generated, so the durable shape of a Delivery is readable in one place.
 */
@Repository
class DeliveryRepository {

	private final JdbcClient jdbcClient;

	DeliveryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	void insertDelivery(UUID id, DeliveryRequests.Create request, Instant createdAt) {
		this.jdbcClient.sql("""
				INSERT INTO delivery (id, reference, pickup_address_label, pickup_latitude, pickup_longitude,
				                      handoff_address_label, handoff_latitude, handoff_longitude,
				                      state, version, created_at, updated_at)
				VALUES (:id, :reference, :pickupLabel, :pickupLatitude, :pickupLongitude,
				        :handoffLabel, :handoffLatitude, :handoffLongitude,
				        :state, 0, :createdAt, :createdAt)
				""")
			.param("id", id)
			.param("reference", request.reference().strip())
			.param("pickupLabel", request.pickup().addressLabel().strip())
			.param("pickupLatitude", request.pickup().latitude())
			.param("pickupLongitude", request.pickup().longitude())
			.param("handoffLabel", request.handoff().addressLabel().strip())
			.param("handoffLatitude", request.handoff().latitude())
			.param("handoffLongitude", request.handoff().longitude())
			.param("state", DeliveryState.AWAITING_COURIER.name())
			.param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
			.update();
	}

	void insertTransition(UUID deliveryId, DeliveryState previousState, DeliveryState nextState, CurrentActor actor,
			CancellationReason reasonCode, String reasonNote, UUID commandId, Instant occurredAt) {
		this.jdbcClient.sql("""
				INSERT INTO delivery_transition (id, delivery_id, previous_state, next_state, actor_account_id,
				                                 actor_display_name, reason_code, reason_note, command_id, occurred_at)
				VALUES (:id, :deliveryId, :previousState, :nextState, :actorAccountId,
				        :actorDisplayName, :reasonCode, :reasonNote, :commandId, :occurredAt)
				""")
			.param("id", UUID.randomUUID())
			.param("deliveryId", deliveryId)
			.param("previousState", (previousState == null) ? null : previousState.name())
			.param("nextState", nextState.name())
			.param("actorAccountId", actor.accountId())
			.param("actorDisplayName", actor.displayName())
			.param("reasonCode", (reasonCode == null) ? null : reasonCode.name())
			.param("reasonNote", (reasonNote == null || reasonNote.isBlank()) ? null : reasonNote.strip())
			.param("commandId", commandId)
			.param("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
			.update();
	}

	/**
	 * Locks the row so two concurrent commands for the same Delivery are decided one after the
	 * other rather than both reading the same version.
	 */
	Optional<CurrentState> lockCurrentState(UUID id) {
		return this.jdbcClient.sql("SELECT id, state, version FROM delivery WHERE id = :id FOR UPDATE")
			.param("id", id)
			.query((rs, rowNumber) -> new CurrentState(rs.getObject("id", UUID.class),
					DeliveryState.valueOf(rs.getString("state")), rs.getInt("version")))
			.optional();
	}

	Optional<DeliveryAssignmentOperations.AssignmentTarget> findAssignmentTarget(UUID id) {
		return this.jdbcClient.sql("""
				SELECT id, state, version, pickup_latitude, pickup_longitude
				FROM delivery WHERE id = :id
				""")
			.param("id", id)
			.query((rs, rowNumber) -> assignmentTarget(rs))
			.optional();
	}

	Optional<DeliveryAssignmentOperations.AssignmentTarget> lockAssignmentTarget(UUID id) {
		return this.jdbcClient.sql("""
				SELECT id, state, version, pickup_latitude, pickup_longitude
				FROM delivery WHERE id = :id FOR UPDATE
				""")
			.param("id", id)
			.query((rs, rowNumber) -> assignmentTarget(rs))
			.optional();
	}

	int markState(UUID id, int expectedVersion, DeliveryState state, Instant updatedAt) {
		return this.jdbcClient.sql("""
				UPDATE delivery
				SET state = :state, version = version + 1, updated_at = :updatedAt
				WHERE id = :id AND version = :expectedVersion
				""")
			.param("state", state.name())
			.param("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
			.param("id", id)
			.param("expectedVersion", expectedVersion)
			.update();
	}

	private static DeliveryAssignmentOperations.AssignmentTarget assignmentTarget(ResultSet rs) throws SQLException {
		return new DeliveryAssignmentOperations.AssignmentTarget(rs.getObject("id", UUID.class),
				DeliveryState.valueOf(rs.getString("state")), rs.getInt("version"), rs.getDouble("pickup_latitude"),
				rs.getDouble("pickup_longitude"));
	}

	Optional<UUID> findDeliveryIdByCommandId(UUID commandId) {
		return findCommandById(commandId).map(HandledCommand::deliveryId);
	}

	Optional<HandledCommand> findCommandById(UUID commandId) {
		return this.jdbcClient.sql("""
				SELECT delivery_id, next_state FROM delivery_transition WHERE command_id = :commandId
				""")
			.param("commandId", commandId)
			.query((rs, rowNumber) -> new HandledCommand(rs.getObject("delivery_id", UUID.class),
					DeliveryState.valueOf(rs.getString("next_state"))))
			.optional();
	}

	List<DeliveryViews.Summary> findAllNewestFirst() {
		return this.jdbcClient.sql("""
				SELECT id, reference, state, version, pickup_address_label, handoff_address_label,
				       created_at, updated_at
				FROM delivery
				ORDER BY created_at DESC, reference DESC
				""")
			.query((rs, rowNumber) -> new DeliveryViews.Summary(rs.getObject("id", UUID.class),
					rs.getString("reference"), DeliveryState.valueOf(rs.getString("state")), rs.getInt("version"),
					rs.getString("pickup_address_label"), rs.getString("handoff_address_label"),
					instant(rs, "created_at"), instant(rs, "updated_at")))
			.list();
	}

	Optional<DeliveryViews.Detail> findDetail(UUID id) {
		List<DeliveryViews.Transition> transitions = findTransitions(id);
		return this.jdbcClient.sql("""
				SELECT id, reference, state, version,
				       pickup_address_label, pickup_latitude, pickup_longitude,
				       handoff_address_label, handoff_latitude, handoff_longitude,
				       created_at, updated_at
				FROM delivery
				WHERE id = :id
				""")
			.param("id", id)
			.query((rs, rowNumber) -> new DeliveryViews.Detail(rs.getObject("id", UUID.class), rs.getString("reference"),
					DeliveryState.valueOf(rs.getString("state")), rs.getInt("version"),
					new DeliveryViews.Address(rs.getString("pickup_address_label"), rs.getDouble("pickup_latitude"),
							rs.getDouble("pickup_longitude")),
					new DeliveryViews.Address(rs.getString("handoff_address_label"), rs.getDouble("handoff_latitude"),
							rs.getDouble("handoff_longitude")),
					instant(rs, "created_at"), instant(rs, "updated_at"), transitions, null))
			.optional();
	}

	Optional<DeliveryViews.CourierDelivery> findCourierDelivery(UUID id) {
		return this.jdbcClient.sql("""
				SELECT id, reference, state, version, pickup_address_label, handoff_address_label
				FROM delivery WHERE id = :id
				""")
			.param("id", id)
			.query((rs, rowNumber) -> new DeliveryViews.CourierDelivery(rs.getObject("id", UUID.class),
					rs.getString("reference"), DeliveryState.valueOf(rs.getString("state")), rs.getInt("version"),
					rs.getString("pickup_address_label"), rs.getString("handoff_address_label")))
			.optional();
	}

	private List<DeliveryViews.Transition> findTransitions(UUID deliveryId) {
		return this.jdbcClient.sql("""
				SELECT previous_state, next_state, actor_display_name, reason_code, reason_note, occurred_at
				FROM delivery_transition
				WHERE delivery_id = :deliveryId
				-- The creation transition is the only one without a previous state, so it stays first
				-- even if a later transition somehow shares its timestamp.
				ORDER BY occurred_at, previous_state NULLS FIRST
				""")
			.param("deliveryId", deliveryId)
			.query((rs, rowNumber) -> new DeliveryViews.Transition(state(rs.getString("previous_state")),
					DeliveryState.valueOf(rs.getString("next_state")), rs.getString("actor_display_name"),
					reason(rs.getString("reason_code")), rs.getString("reason_note"), instant(rs, "occurred_at")))
			.list();
	}

	private static DeliveryState state(String value) {
		return (value == null) ? null : DeliveryState.valueOf(value);
	}

	private static CancellationReason reason(String value) {
		return (value == null) ? null : CancellationReason.valueOf(value);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

	/** Just enough of a Delivery to decide whether a command may be applied to it. */
	record CurrentState(UUID id, DeliveryState state, int version) {
	}

	record HandledCommand(UUID deliveryId, DeliveryState nextState) {
	}

}
