package com.deliveryglance.proof;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The bodies the proof API accepts. Neither ever carries bytes: the upload ticket names an artifact
 * to be uploaded elsewhere, and the processing callback names one already stored.
 */
final class ProofRequests {

	private ProofRequests() {
	}

	/**
	 * What a Courier's browser sends to ask for an upload URL — the kind of artifact and the content
	 * type it intends to send, which the presigned URL then binds so the object cannot later be
	 * uploaded as something else.
	 */
	record UploadTicket(
			@NotNull(message = "is required") ProofArtifactKind kind,

			@NotBlank(message = "is required") String contentType) {
	}

	/**
	 * What the processing Lambda sends back once it has validated an upload. It names the raw object
	 * by key and reports the outcome; a {@code READY} outcome also carries the cleaned and thumbnail
	 * keys and the content hash, which the service requires before it will mark a proof ready.
	 */
	record ProcessingCallback(
			@NotBlank(message = "is required") String rawObjectKey,

			@NotNull(message = "is required") ProcessingOutcome outcome,

			String cleanObjectKey,

			String thumbnailObjectKey,

			String contentHash,

			@NotNull(message = "is required") Instant processedAt) {
	}

}
