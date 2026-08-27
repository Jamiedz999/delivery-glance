package com.deliveryglance.proof;

import java.util.UUID;

import com.deliveryglance.delivery.ActiveAssignments;
import com.deliveryglance.identityaccess.CurrentActorProvider;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import org.springframework.stereotype.Service;

/**
 * Mints a short-lived presigned {@code PUT} so a Courier's browser uploads a captured artifact
 * straight to S3, never through this application. The URL is scoped to one freshly named object key
 * under the Delivery's {@code raw/} prefix and binds the content type the browser declared, so the
 * capability it hands out is "write this one object, once, as this type" and nothing more.
 *
 * <p>The only authorization is the one that matters for capture: the signed-in Courier must hold
 * the active Assignment for the Delivery. That is the same fact a handoff command checks, read
 * through the same port, so a Courier cannot mint an upload for a Delivery they are not carrying.
 */
@Service
class ProofUploads {

	private final S3Presigner presigner;

	private final ProofProperties properties;

	private final ActiveAssignments assignments;

	private final CurrentActorProvider currentActorProvider;

	ProofUploads(S3Presigner presigner, ProofProperties properties, ActiveAssignments assignments,
			CurrentActorProvider currentActorProvider) {
		this.presigner = presigner;
		this.properties = properties;
		this.assignments = assignments;
		this.currentActorProvider = currentActorProvider;
	}

	IssuedUpload issue(UUID deliveryId, ProofArtifactKind kind, String contentType) {
		if (!this.properties.isConfigured()) {
			throw ProofException.storageUnavailable();
		}
		if (!this.properties.allowsContentType(contentType)) {
			throw ProofException.unsupportedContentType();
		}
		requireCarryingCourier(deliveryId);

		String objectKey = ProofObjectKeys.newRawKey(deliveryId, kind);
		PutObjectRequest put = PutObjectRequest.builder()
			.bucket(this.properties.bucket())
			.key(objectKey)
			.contentType(contentType)
			.build();
		PresignedPutObjectRequest presigned = this.presigner
			.presignPutObject(builder -> builder.signatureDuration(this.properties.presignExpiry()).putObjectRequest(put));
		return new IssuedUpload(presigned.url().toString(), objectKey);
	}

	private void requireCarryingCourier(UUID deliveryId) {
		UUID courierId = this.currentActorProvider.requireCurrentActor().accountId();
		this.assignments.activeForDelivery(deliveryId)
			.filter((assignment) -> assignment.courierId().equals(courierId))
			.orElseThrow(ProofException::notCarryingDelivery);
	}

	/** Everything the browser needs to upload one artifact and later name it on the handoff command. */
	record IssuedUpload(String uploadUrl, String objectKey) {
	}

}
