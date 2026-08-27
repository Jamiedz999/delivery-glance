package com.deliveryglance.proof;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles a pending proof from the processing Lambda's callback. The Lambda is trusted — it is the
 * only caller that holds the shared token — so this adds no policy beyond the table's: a {@code
 * READY} outcome must actually carry the cleaned key, thumbnail key and hash it claims, and a
 * callback for an object no proof is waiting on is refused rather than silently ignored.
 *
 * <p>Settling is idempotent. The update statements only touch a {@code PENDING} row, so a callback
 * that arrives twice — S3 can deliver an event more than once — settles the proof the first time
 * and changes nothing the second, without failing.
 */
@Service
class ProofProcessing {

	private final DeliveryProofRepository repository;

	ProofProcessing(DeliveryProofRepository repository) {
		this.repository = repository;
	}

	@Transactional
	void settle(ProofRequests.ProcessingCallback callback) {
		if (this.repository.deliveryIdForRawKey(callback.rawObjectKey()).isEmpty()) {
			throw ProofException.unknownObject();
		}
		if (callback.outcome() == ProcessingOutcome.READY) {
			if (blank(callback.cleanObjectKey()) || blank(callback.thumbnailObjectKey())
					|| blank(callback.contentHash())) {
				throw ProofException.incompleteReadyCallback();
			}
			this.repository.markReady(callback.rawObjectKey(), callback.cleanObjectKey(),
					callback.thumbnailObjectKey(), callback.contentHash(), callback.processedAt());
		}
		else {
			this.repository.markRejected(callback.rawObjectKey(), callback.processedAt());
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
