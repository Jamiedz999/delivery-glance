package com.deliveryglance.proof;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Records a handoff's captured artifacts, one {@code PENDING} row each. It runs in the caller's
 * transaction and adds no policy of its own beyond the one that keeps a forged key out: a submitted
 * key is stored only if it is a raw key this application would have minted for this Delivery and
 * this kind. Anything else is refused, which rolls the handoff back with it.
 */
@Service
class ProofAttachments implements DeliveryProofAttachments {

	private final DeliveryProofRepository repository;

	ProofAttachments(DeliveryProofRepository repository) {
		this.repository = repository;
	}

	@Override
	public void attachAtHandoff(UUID deliveryId, ProofKeys keys, Instant capturedAt) {
		if (keys == null || keys.isEmpty()) {
			return;
		}
		attach(deliveryId, keys.photoObjectKey(), ProofArtifactKind.PHOTO, capturedAt);
		attach(deliveryId, keys.signatureObjectKey(), ProofArtifactKind.SIGNATURE, capturedAt);
	}

	private void attach(UUID deliveryId, String objectKey, ProofArtifactKind expectedKind, Instant capturedAt) {
		if (objectKey == null || objectKey.isBlank()) {
			return;
		}
		ProofObjectKeys.ParsedRawKey parsed = ProofObjectKeys.parseRawKeyFor(deliveryId, objectKey)
			.filter((key) -> key.kind() == expectedKind)
			.orElseThrow(ProofException::invalidObjectKey);
		this.repository.insertPending(deliveryId, parsed.kind(), parsed.objectKey(), capturedAt);
	}

}
