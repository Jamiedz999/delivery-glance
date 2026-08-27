package com.deliveryglance.proof;

import java.time.Instant;
import java.util.List;

/**
 * What a Dispatcher's browser is allowed to see about a Delivery's proof. It never carries an
 * object key: a viewable artifact is a short-lived presigned URL the browser loads directly from
 * S3, and one that is not yet ready, or was rejected, carries a status and no URL at all.
 */
final class ProofViews {

	private ProofViews() {
	}

	record ProofSet(List<Artifact> artifacts) {
	}

	record Artifact(ProofArtifactKind kind, ProofStatus status, Instant capturedAt, Instant processedAt,
			String thumbnailUrl, String fullUrl) {
	}

}
