package com.deliveryglance.proof;

import java.util.List;
import java.util.UUID;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import org.springframework.stereotype.Service;

/**
 * The Dispatcher's read of a Delivery's proof. It turns stored object keys into short-lived
 * presigned {@code GET} URLs so the browser loads the image straight from the private bucket, which
 * is the only way a private object is ever shown: no proof byte passes through the application on
 * the way out any more than it did on the way in.
 *
 * <p>Only a {@code READY} artifact yields URLs. A {@code PENDING} one is still being processed and a
 * {@code REJECTED} one is quarantined, so both come back as a status with nothing to load — a
 * Dispatcher is never handed a link to an object that is missing or unsafe.
 */
@Service
class ProofViewing {

	private final DeliveryProofRepository repository;

	private final S3Presigner presigner;

	private final ProofProperties properties;

	ProofViewing(DeliveryProofRepository repository, S3Presigner presigner, ProofProperties properties) {
		this.repository = repository;
		this.presigner = presigner;
		this.properties = properties;
	}

	ProofViews.ProofSet forDelivery(UUID deliveryId) {
		List<ProofViews.Artifact> artifacts = this.repository.findForDelivery(deliveryId)
			.stream()
			.map(this::toArtifact)
			.toList();
		return new ProofViews.ProofSet(artifacts);
	}

	private ProofViews.Artifact toArtifact(DeliveryProofRepository.StoredProof proof) {
		boolean ready = proof.status() == ProofStatus.READY;
		return new ProofViews.Artifact(proof.kind(), proof.status(), proof.capturedAt(), proof.processedAt(),
				ready ? presignedGet(proof.thumbnailObjectKey()) : null,
				ready ? presignedGet(proof.cleanObjectKey()) : null);
	}

	private String presignedGet(String objectKey) {
		GetObjectRequest get = GetObjectRequest.builder().bucket(this.properties.bucket()).key(objectKey).build();
		return this.presigner
			.presignGetObject(builder -> builder.signatureDuration(this.properties.presignExpiry()).getObjectRequest(get))
			.url()
			.toString();
	}

}
