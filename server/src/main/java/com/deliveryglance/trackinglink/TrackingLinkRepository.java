package com.deliveryglance.trackinglink;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit SQL for the three Tracking Link tables. Nothing here writes a raw token or a complete
 * Tracking URL, and there is no column that could hold one.
 */
@Repository
class TrackingLinkRepository {

	private final JdbcClient jdbcClient;

	TrackingLinkRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	void insertLink(UUID deliveryId, UUID linkId, int generation, int keyVersion, String tokenVerifier,
			Instant issuedAt, Instant expiresAt) {
		this.jdbcClient.sql("""
				INSERT INTO tracking_link (delivery_id, link_id, generation, key_version, token_verifier,
				                           issued_at, expires_at)
				VALUES (:deliveryId, :linkId, :generation, :keyVersion, :tokenVerifier, :issuedAt, :expiresAt)
				""")
			.param("deliveryId", deliveryId)
			.param("linkId", linkId)
			.param("generation", generation)
			.param("keyVersion", keyVersion)
			.param("tokenVerifier", tokenVerifier)
			.param("issuedAt", utc(issuedAt))
			.param("expiresAt", utc(expiresAt))
			.update();
	}

	Optional<StoredLink> findByDelivery(UUID deliveryId) {
		return this.jdbcClient.sql("""
				SELECT delivery_id, link_id, generation, key_version, token_verifier, expires_at
				FROM tracking_link WHERE delivery_id = :deliveryId
				""").param("deliveryId", deliveryId).query(TrackingLinkRepository::storedLink).optional();
	}

	/**
	 * Looks the link up by verifier rather than by anything decodable from the token, so a token
	 * that is merely well-formed reveals nothing about which Deliveries exist.
	 */
	Optional<StoredLink> findByTokenVerifier(String tokenVerifier) {
		return this.jdbcClient.sql("""
				SELECT delivery_id, link_id, generation, key_version, token_verifier, expires_at
				FROM tracking_link WHERE token_verifier = :tokenVerifier
				""").param("tokenVerifier", tokenVerifier).query(TrackingLinkRepository::storedLink).optional();
	}

	void insertGrant(UUID id, UUID linkId, int generation, String secretVerifier, Instant establishedAt,
			Instant expiresAt) {
		this.jdbcClient.sql("""
				INSERT INTO tracking_grant (id, link_id, generation, secret_verifier, established_at, expires_at)
				VALUES (:id, :linkId, :generation, :secretVerifier, :establishedAt, :expiresAt)
				""")
			.param("id", id)
			.param("linkId", linkId)
			.param("generation", generation)
			.param("secretVerifier", secretVerifier)
			.param("establishedAt", utc(establishedAt))
			.param("expiresAt", utc(expiresAt))
			.update();
	}

	Optional<StoredGrant> findGrantByVerifier(String secretVerifier) {
		return this.jdbcClient.sql("""
				SELECT g.link_id, g.generation, g.expires_at, l.delivery_id, l.generation AS link_generation
				FROM tracking_grant g JOIN tracking_link l ON l.link_id = g.link_id
				WHERE g.secret_verifier = :secretVerifier
				""")
			.param("secretVerifier", secretVerifier)
			.query((rs, rowNumber) -> new StoredGrant(rs.getObject("link_id", UUID.class), rs.getInt("generation"),
					rs.getObject("delivery_id", UUID.class), rs.getInt("link_generation"), instant(rs, "expires_at")))
			.optional();
	}

	void insertCopy(UUID linkId, UUID actorAccountId, Instant copiedAt) {
		this.jdbcClient.sql("""
				INSERT INTO tracking_link_copy (id, link_id, actor_account_id, copied_at)
				VALUES (:id, :linkId, :actorAccountId, :copiedAt)
				""")
			.param("id", UUID.randomUUID())
			.param("linkId", linkId)
			.param("actorAccountId", actorAccountId)
			.param("copiedAt", utc(copiedAt))
			.update();
	}

	private static StoredLink storedLink(ResultSet rs, int rowNumber) throws SQLException {
		return new StoredLink(rs.getObject("delivery_id", UUID.class), rs.getObject("link_id", UUID.class),
				rs.getInt("generation"), rs.getInt("key_version"), rs.getString("token_verifier"),
				instant(rs, "expires_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	/** @param expiresAt the seven-day cap only; the terminal grace period is applied by the caller */
	record StoredLink(UUID deliveryId, UUID linkId, int generation, int keyVersion, String tokenVerifier,
			Instant expiresAt) {
	}

	/**
	 * @param generation the generation the grant was established through
	 * @param linkGeneration the link's generation now, so a grant from a superseded one is refused
	 */
	record StoredGrant(UUID linkId, int generation, UUID deliveryId, int linkGeneration, Instant expiresAt) {
	}

}
