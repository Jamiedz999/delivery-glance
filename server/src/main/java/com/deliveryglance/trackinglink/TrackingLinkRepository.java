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

	/**
	 * Reads the link for a Dispatcher command that may change it — Copy or Revocation — taking a row
	 * lock so the two serialise. Copy and Revocation both decide on the link's status, so letting
	 * them run against the same row without a lock is what would let a Copy hand out a link the
	 * concurrent Revocation is about to end.
	 */
	Optional<StoredLink> lockByDelivery(UUID deliveryId) {
		return this.jdbcClient.sql("""
				SELECT delivery_id, link_id, generation, key_version, token_verifier, expires_at, status
				FROM tracking_link WHERE delivery_id = :deliveryId
				FOR UPDATE
				""").param("deliveryId", deliveryId).query(TrackingLinkRepository::storedLink).optional();
	}

	/**
	 * Looks the link up by verifier rather than by anything decodable from the token, so a token
	 * that is merely well-formed reveals nothing about which Deliveries exist.
	 */
	Optional<StoredLink> findByTokenVerifier(String tokenVerifier) {
		return this.jdbcClient.sql("""
				SELECT delivery_id, link_id, generation, key_version, token_verifier, expires_at, status
				FROM tracking_link WHERE token_verifier = :tokenVerifier
				""").param("tokenVerifier", tokenVerifier).query(TrackingLinkRepository::storedLink).optional();
	}

	/** Marks the link revoked. The audit — actor, reason, note — is a separate row. */
	void markRevoked(UUID linkId, Instant revokedAt) {
		this.jdbcClient.sql("""
				UPDATE tracking_link SET status = 'revoked', revoked_at = :revokedAt
				WHERE link_id = :linkId
				""").param("linkId", linkId).param("revokedAt", utc(revokedAt)).update();
	}

	void insertRevocation(UUID id, UUID linkId, UUID actorAccountId, TrackingLinkChangeReason reason, String note,
			Instant revokedAt) {
		this.jdbcClient.sql("""
				INSERT INTO tracking_link_revocation (id, link_id, actor_account_id, reason, note, revoked_at)
				VALUES (:id, :linkId, :actorAccountId, :reason, :note, :revokedAt)
				""")
			.param("id", id)
			.param("linkId", linkId)
			.param("actorAccountId", actorAccountId)
			.param("reason", reason.name())
			.param("note", note)
			.param("revokedAt", utc(revokedAt))
			.update();
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

	/**
	 * Carries the link's own cap alongside the grant's, so authorizing a read is one query rather
	 * than a second trip back for the link this statement has already joined to.
	 */
	Optional<StoredGrant> findGrantByVerifier(String secretVerifier) {
		return this.jdbcClient.sql("""
				SELECT g.generation, g.expires_at, l.delivery_id, l.generation AS link_generation,
				       l.expires_at AS link_expires_at, l.status AS link_status
				FROM tracking_grant g JOIN tracking_link l ON l.link_id = g.link_id
				WHERE g.secret_verifier = :secretVerifier
				""")
			.param("secretVerifier", secretVerifier)
			.query((rs, rowNumber) -> new StoredGrant(rs.getInt("generation"),
					rs.getObject("delivery_id", UUID.class), rs.getInt("link_generation"),
					instant(rs, "expires_at"), instant(rs, "link_expires_at"), isRevoked(rs, "link_status")))
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
				instant(rs, "expires_at"), isRevoked(rs, "status"));
	}

	private static boolean isRevoked(ResultSet rs, String column) throws SQLException {
		return "revoked".equals(rs.getString(column));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	/**
	 * @param expiresAt the seven-day cap only; the terminal grace period is applied by the caller
	 * @param revoked whether a Dispatcher has ended access through this link; a revoked link is never
	 * exchanged, read through, or copied again
	 */
	record StoredLink(UUID deliveryId, UUID linkId, int generation, int keyVersion, String tokenVerifier,
			Instant expiresAt, boolean revoked) {
	}

	/**
	 * @param generation the generation the grant was established through
	 * @param linkGeneration the link's generation now, so a grant from a superseded one is refused
	 * @param expiresAt the grant's own bound, fixed when it was established
	 * @param linkExpiresAt the link's seven-day cap, which the terminal grace period may shorten
	 * below the grant's bound after the grant was issued
	 * @param linkRevoked whether the link the grant derives from has been revoked, so a grant
	 * established before the Revocation stops authorizing on its next read
	 */
	record StoredGrant(int generation, UUID deliveryId, int linkGeneration, Instant expiresAt,
			Instant linkExpiresAt, boolean linkRevoked) {
	}

}
