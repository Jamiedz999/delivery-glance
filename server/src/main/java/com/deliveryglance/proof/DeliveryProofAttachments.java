package com.deliveryglance.proof;

import java.time.Instant;
import java.util.UUID;

/**
 * The one thing the Delivery module asks of proof: record the artifacts a completed handoff
 * captured. It is called from inside the handoff transaction, so the proof references and the
 * transition that explains them commit together or not at all — a handoff never lands without its
 * proof, and proof never lands without its handoff.
 *
 * <p>The keys are whatever the client attached to the command, so the implementation treats them as
 * untrusted: each is checked against the Delivery it claims to belong to before it is stored.
 */
public interface DeliveryProofAttachments {

	void attachAtHandoff(UUID deliveryId, ProofKeys keys, Instant capturedAt);

	/**
	 * The raw object keys a handoff carried, either of which may be absent because proof is optional.
	 * Both absent is the ordinary handoff with no capture.
	 */
	record ProofKeys(String photoObjectKey, String signatureObjectKey) {

		public boolean isEmpty() {
			return blank(this.photoObjectKey) && blank(this.signatureObjectKey);
		}

		private static boolean blank(String value) {
			return value == null || value.isBlank();
		}

	}

}
