package com.deliveryglance.location;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The durable half of Location Sharing: which session may report, and how to check that it is the
 * one talking. Everything the session produces stays in memory.
 */
@Repository
class LocationSharingRepository {

	private final JdbcClient jdbcClient;

	LocationSharingRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	/**
	 * Replaces any session the Courier already had, so starting again from a second tab leaves
	 * exactly one session able to report rather than two competing ones.
	 */
	void save(UUID courierAccountId, UUID generation, String reportingSecretVerifier, Instant startedAt) {
		this.jdbcClient.sql("""
				INSERT INTO courier_location_sharing (courier_account_id, generation, reporting_secret_verifier,
				                                      started_at)
				VALUES (:courierAccountId, :generation, :verifier, :startedAt)
				ON CONFLICT (courier_account_id) DO UPDATE
				SET generation = EXCLUDED.generation,
				    reporting_secret_verifier = EXCLUDED.reporting_secret_verifier,
				    started_at = EXCLUDED.started_at
				""")
			.param("courierAccountId", courierAccountId)
			.param("generation", generation)
			.param("verifier", reportingSecretVerifier)
			.param("startedAt", OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC))
			.update();
	}

	Optional<CurrentSession> find(UUID courierAccountId) {
		return this.jdbcClient.sql("""
				SELECT generation, reporting_secret_verifier, started_at
				FROM courier_location_sharing
				WHERE courier_account_id = :courierAccountId
				""")
			.param("courierAccountId", courierAccountId)
			.query((rs, rowNumber) -> new CurrentSession(rs.getObject("generation", UUID.class),
					rs.getString("reporting_secret_verifier"),
					rs.getObject("started_at", OffsetDateTime.class).toInstant()))
			.optional();
	}

	void delete(UUID courierAccountId) {
		this.jdbcClient.sql("DELETE FROM courier_location_sharing WHERE courier_account_id = :courierAccountId")
			.param("courierAccountId", courierAccountId)
			.update();
	}

	record CurrentSession(UUID generation, String reportingSecretVerifier, Instant startedAt) {
	}

}
