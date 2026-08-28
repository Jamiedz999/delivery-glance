package com.deliveryglance.eta;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit SQL for the one ETA table. A Delivery has at most one current window; every write is an
 * upsert on {@code delivery_id} and a withdrawal is a delete, so the row's presence is exactly "there
 * is a current ETA" with nothing to reconcile.
 */
@Repository
class EtaRepository implements EtaStore {

	private final JdbcClient jdbcClient;

	EtaRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Optional<StoredEta> find(UUID deliveryId) {
		return this.jdbcClient.sql("""
				SELECT window_start, window_end, calculated_at
				FROM delivery_eta
				WHERE delivery_id = :deliveryId
				""")
			.param("deliveryId", deliveryId)
			.query((rs, rowNumber) -> new StoredEta(
					new EtaWindow(rs.getObject("window_start", OffsetDateTime.class).toInstant(),
							rs.getObject("window_end", OffsetDateTime.class).toInstant()),
					rs.getObject("calculated_at", OffsetDateTime.class).toInstant()))
			.optional();
	}

	/**
	 * Stores the window for a Delivery, replacing any current one. On a recalculation that did not move
	 * the endpoints the caller passes the endpoints it already holds and a fresh {@code calculatedAt},
	 * so the window stays put while its freshness advances.
	 */
	@Override
	public void upsert(UUID deliveryId, EtaWindow window, Instant calculatedAt) {
		this.jdbcClient.sql("""
				INSERT INTO delivery_eta
					(id, delivery_id, window_start, window_end, calculated_at, created_at, updated_at)
				VALUES (:id, :deliveryId, :windowStart, :windowEnd, :calculatedAt, :calculatedAt, :calculatedAt)
				ON CONFLICT (delivery_id) DO UPDATE
					SET window_start = :windowStart, window_end = :windowEnd,
						calculated_at = :calculatedAt, updated_at = :calculatedAt
				""")
			.param("id", UUID.randomUUID())
			.param("deliveryId", deliveryId)
			.param("windowStart", offset(window.start()))
			.param("windowEnd", offset(window.end()))
			.param("calculatedAt", offset(calculatedAt))
			.update();
	}

	@Override
	public void delete(UUID deliveryId) {
		this.jdbcClient.sql("DELETE FROM delivery_eta WHERE delivery_id = :deliveryId")
			.param("deliveryId", deliveryId)
			.update();
	}

	private static OffsetDateTime offset(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
