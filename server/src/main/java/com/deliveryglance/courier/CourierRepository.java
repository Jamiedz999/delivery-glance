package com.deliveryglance.courier;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The single durable Courier fact: On Duty and since when.
 */
@Repository
class CourierRepository {

	private final JdbcClient jdbcClient;

	CourierRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<Duty> findDuty(UUID accountId) {
		return this.jdbcClient.sql("SELECT on_duty, on_duty_changed_at FROM courier WHERE account_id = :accountId")
			.param("accountId", accountId)
			.query(CourierRepository::duty)
			.optional();
	}

	List<CourierAvailability.Courier> findAllCourierAvailability() {
		return this.jdbcClient.sql("""
				SELECT account.id, account.display_name, coalesce(courier.on_duty, false) AS on_duty
				FROM internal_account account
				LEFT JOIN courier ON courier.account_id = account.id
				WHERE account.role = 'COURIER' AND account.enabled
				ORDER BY account.id
				""")
			.query((rs, rowNumber) -> availability(rs))
			.list();
	}

	Optional<CourierAvailability.Courier> lockCourierAvailability(UUID accountId) {
		return this.jdbcClient.sql("""
				SELECT account.id, account.display_name, courier.on_duty
				FROM internal_account account
				JOIN courier ON courier.account_id = account.id
				WHERE account.id = :accountId AND account.role = 'COURIER' AND account.enabled
				FOR UPDATE OF account, courier
				""")
			.param("accountId", accountId)
			.query((rs, rowNumber) -> availability(rs))
			.optional();
	}

	/**
	 * Writes the Courier's declaration and answers with the row as it now stands. Repeating the
	 * current value leaves the timestamp alone, so "On Duty since" keeps meaning since when rather
	 * than when the button was last pressed.
	 */
	Duty saveDuty(UUID accountId, boolean onDuty, Instant changedAt) {
		return this.jdbcClient.sql("""
				INSERT INTO courier (account_id, on_duty, on_duty_changed_at)
				VALUES (:accountId, :onDuty, :changedAt)
				ON CONFLICT (account_id) DO UPDATE
				SET on_duty = EXCLUDED.on_duty,
				    on_duty_changed_at = CASE WHEN courier.on_duty <> EXCLUDED.on_duty
				                              THEN EXCLUDED.on_duty_changed_at
				                              ELSE courier.on_duty_changed_at END
				RETURNING on_duty, on_duty_changed_at
				""")
			.param("accountId", accountId)
			.param("onDuty", onDuty)
			.param("changedAt", OffsetDateTime.ofInstant(changedAt, ZoneOffset.UTC))
			.query(CourierRepository::duty)
			.single();
	}

	private static Duty duty(ResultSet rs, int rowNumber) throws SQLException {
		return new Duty(rs.getBoolean("on_duty"), rs.getObject("on_duty_changed_at", OffsetDateTime.class).toInstant());
	}

	private static CourierAvailability.Courier availability(ResultSet rs) throws SQLException {
		return new CourierAvailability.Courier(rs.getObject("id", UUID.class), rs.getString("display_name"),
				rs.getBoolean("on_duty"));
	}

	record Duty(boolean onDuty, Instant changedAt) {
	}

}
