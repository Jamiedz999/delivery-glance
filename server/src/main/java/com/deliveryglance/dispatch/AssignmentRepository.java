package com.deliveryglance.dispatch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.deliveryglance.delivery.ActiveAssignments;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AssignmentRepository implements ActiveAssignments {

	private final JdbcClient jdbcClient;

	AssignmentRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	void insert(UUID deliveryId, UUID courierId, UUID commandId, Instant assignedAt) {
		this.jdbcClient.sql("""
				INSERT INTO assignment (id, delivery_id, courier_account_id, command_id, assigned_at)
				VALUES (:id, :deliveryId, :courierId, :commandId, :assignedAt)
				""")
			.param("id", UUID.randomUUID())
			.param("deliveryId", deliveryId)
			.param("courierId", courierId)
			.param("commandId", commandId)
			.param("assignedAt", timestamp(assignedAt))
			.update();
	}

	Optional<UUID> deliveryIdForCommand(UUID commandId) {
		return this.jdbcClient.sql("SELECT delivery_id FROM assignment WHERE command_id = :commandId")
			.param("commandId", commandId)
			.query((rs, rowNumber) -> rs.getObject("delivery_id", UUID.class))
			.optional();
	}

	Set<UUID> activeCourierIds() {
		return Set.copyOf(this.jdbcClient.sql("SELECT courier_account_id FROM assignment WHERE ended_at IS NULL")
			.query((rs, rowNumber) -> rs.getObject("courier_account_id", UUID.class))
			.list());
	}

	@Override
	public Optional<ActiveAssignment> activeForDelivery(UUID deliveryId) {
		return this.jdbcClient.sql("""
				SELECT assignment.delivery_id, assignment.courier_account_id, account.display_name,
				       assignment.assigned_at
				FROM assignment
				JOIN internal_account account ON account.id = assignment.courier_account_id
				WHERE assignment.delivery_id = :deliveryId AND assignment.ended_at IS NULL
				""")
			.param("deliveryId", deliveryId)
			.query((rs, rowNumber) -> activeAssignment(rs))
			.optional();
	}

	@Override
	public Optional<ActiveAssignment> activeForCourier(UUID courierId) {
		return this.jdbcClient.sql("""
				SELECT assignment.delivery_id, assignment.courier_account_id, account.display_name,
				       assignment.assigned_at
				FROM assignment
				JOIN internal_account account ON account.id = assignment.courier_account_id
				WHERE assignment.courier_account_id = :courierId AND assignment.ended_at IS NULL
				""")
			.param("courierId", courierId)
			.query((rs, rowNumber) -> activeAssignment(rs))
			.optional();
	}

	@Override
	public void endForDelivery(UUID deliveryId, Instant endedAt) {
		this.jdbcClient.sql("""
				UPDATE assignment SET ended_at = :endedAt
				WHERE delivery_id = :deliveryId AND ended_at IS NULL
				""")
			.param("endedAt", timestamp(endedAt))
			.param("deliveryId", deliveryId)
			.update();
	}

	private static ActiveAssignment activeAssignment(ResultSet rs) throws SQLException {
		return new ActiveAssignment(rs.getObject("delivery_id", UUID.class),
				rs.getObject("courier_account_id", UUID.class), rs.getString("display_name"),
				rs.getObject("assigned_at", OffsetDateTime.class).toInstant());
	}

	private static OffsetDateTime timestamp(Instant value) {
		return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
	}

}
