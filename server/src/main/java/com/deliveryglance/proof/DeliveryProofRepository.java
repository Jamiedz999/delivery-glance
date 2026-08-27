package com.deliveryglance.proof;

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
 * Explicit SQL for {@code delivery_proof}. The row holds references, never bytes, so every column
 * here is an object key, a hash or a time.
 *
 * <p>The write side has exactly three shapes, matching the table's only lifecycle: a handoff
 * attaches a {@code PENDING} row, and the Lambda's callback settles it {@code READY} or
 * {@code REJECTED} by its raw key. The settle statements are guarded on {@code status = 'PENDING'}
 * so a duplicate callback changes nothing rather than reopening a processed row.
 */
@Repository
class DeliveryProofRepository implements ProofPresence {

	private final JdbcClient jdbcClient;

	DeliveryProofRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	void insertPending(UUID deliveryId, ProofArtifactKind kind, String rawObjectKey, Instant capturedAt) {
		this.jdbcClient.sql("""
				INSERT INTO delivery_proof (id, delivery_id, kind, raw_object_key, status, captured_at)
				VALUES (:id, :deliveryId, :kind, :rawObjectKey, 'PENDING', :capturedAt)
				""")
			.param("id", UUID.randomUUID())
			.param("deliveryId", deliveryId)
			.param("kind", kind.name())
			.param("rawObjectKey", rawObjectKey)
			.param("capturedAt", timestamp(capturedAt))
			.update();
	}

	/** @return the number of rows settled: 1 for the first callback, 0 for a duplicate. */
	int markReady(String rawObjectKey, String cleanObjectKey, String thumbnailObjectKey, String contentHash,
			Instant processedAt) {
		return this.jdbcClient.sql("""
				UPDATE delivery_proof
				SET status = 'READY', clean_object_key = :cleanObjectKey,
				    thumbnail_object_key = :thumbnailObjectKey, content_hash = :contentHash,
				    processed_at = :processedAt
				WHERE raw_object_key = :rawObjectKey AND status = 'PENDING'
				""")
			.param("cleanObjectKey", cleanObjectKey)
			.param("thumbnailObjectKey", thumbnailObjectKey)
			.param("contentHash", contentHash)
			.param("processedAt", timestamp(processedAt))
			.param("rawObjectKey", rawObjectKey)
			.update();
	}

	int markRejected(String rawObjectKey, Instant processedAt) {
		return this.jdbcClient.sql("""
				UPDATE delivery_proof
				SET status = 'REJECTED', processed_at = :processedAt
				WHERE raw_object_key = :rawObjectKey AND status = 'PENDING'
				""")
			.param("processedAt", timestamp(processedAt))
			.param("rawObjectKey", rawObjectKey)
			.update();
	}

	List<StoredProof> findForDelivery(UUID deliveryId) {
		return this.jdbcClient.sql("""
				SELECT kind, status, clean_object_key, thumbnail_object_key, captured_at, processed_at
				FROM delivery_proof
				WHERE delivery_id = :deliveryId
				ORDER BY kind
				""")
			.param("deliveryId", deliveryId)
			.query((rs, rowNumber) -> storedProof(rs))
			.list();
	}

	Optional<UUID> deliveryIdForRawKey(String rawObjectKey) {
		return this.jdbcClient.sql("SELECT delivery_id FROM delivery_proof WHERE raw_object_key = :rawObjectKey")
			.param("rawObjectKey", rawObjectKey)
			.query((rs, rowNumber) -> rs.getObject("delivery_id", UUID.class))
			.optional();
	}

	@Override
	public boolean hasProofOnFile(UUID deliveryId) {
		// READY only: a Recipient is told proof is on file just for an artifact that was validated
		// and scrubbed. A PENDING upload might still be quarantined as not an image, and telling the
		// Recipient it exists before then could claim a proof that never comes to be.
		return this.jdbcClient.sql("""
				SELECT count(*) FROM delivery_proof
				WHERE delivery_id = :deliveryId AND status = 'READY'
				""")
			.param("deliveryId", deliveryId)
			.query(Integer.class)
			.single() > 0;
	}

	private static StoredProof storedProof(ResultSet rs) throws SQLException {
		return new StoredProof(ProofArtifactKind.valueOf(rs.getString("kind")),
				ProofStatus.valueOf(rs.getString("status")), rs.getString("clean_object_key"),
				rs.getString("thumbnail_object_key"), instant(rs, "captured_at"), nullableInstant(rs, "processed_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

	private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value == null) ? null : value.toInstant();
	}

	private static OffsetDateTime timestamp(Instant value) {
		return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
	}

	/** One stored artifact as the read side needs it: its status and the keys to sign, never bytes. */
	record StoredProof(ProofArtifactKind kind, ProofStatus status, String cleanObjectKey, String thumbnailObjectKey,
			Instant capturedAt, Instant processedAt) {
	}

}
