package com.deliveryglance.proof;

import org.springframework.http.HttpStatus;

/**
 * A proof request the application refused, carrying the HTTP status and stable {@code code} its one
 * handler renders. The messages never name S3, a bucket or a key: a Courier is told what they may
 * do, not how the bytes are stored.
 */
class ProofException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private ProofException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	/** No bucket is configured, so no URL can be minted. The feature is off, not the request wrong. */
	static ProofException storageUnavailable() {
		return new ProofException(HttpStatus.SERVICE_UNAVAILABLE, "proof-storage-unavailable",
				"Proof of delivery is not available on this deployment.");
	}

	/** The signed-in Courier does not hold the active Assignment for this Delivery. */
	static ProofException notCarryingDelivery() {
		return new ProofException(HttpStatus.FORBIDDEN, "proof-not-carrying-delivery",
				"Only the Courier carrying this Delivery can capture its proof.");
	}

	static ProofException unsupportedContentType() {
		return new ProofException(HttpStatus.UNPROCESSABLE_ENTITY, "proof-unsupported-content-type",
				"That file type cannot be used as proof of delivery.");
	}

	/** A submitted object key is not one this application minted for this Delivery's handoff. */
	static ProofException invalidObjectKey() {
		return new ProofException(HttpStatus.BAD_REQUEST, "proof-invalid-object-key",
				"A proof reference did not match this Delivery.");
	}

	/** The processing callback presented no valid shared token, so it is not the Lambda. */
	static ProofException callbackUnauthorized() {
		return new ProofException(HttpStatus.UNAUTHORIZED, "proof-callback-unauthorized",
				"The proof processing callback is not authorized.");
	}

	/** A processing callback named a raw key no pending proof is waiting on. */
	static ProofException unknownObject() {
		return new ProofException(HttpStatus.NOT_FOUND, "proof-unknown-object",
				"No pending proof matches that object.");
	}

	/** A READY callback did not carry the cleaned key, thumbnail key and hash a ready proof needs. */
	static ProofException incompleteReadyCallback() {
		return new ProofException(HttpStatus.BAD_REQUEST, "proof-incomplete-callback",
				"A ready proof callback must carry the processed keys and hash.");
	}

	HttpStatus status() {
		return this.status;
	}

	String code() {
		return this.code;
	}

}
