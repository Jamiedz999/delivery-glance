package com.deliveryglance.proof;

/**
 * Where one captured artifact is in its life. It only ever moves forward, and only once: a handoff
 * creates it {@link #PENDING}, and the processing Lambda's callback settles it {@link #READY} or
 * {@link #REJECTED}. A settled row is immutable.
 */
enum ProofStatus {

	/** Uploaded and referenced by a handoff, not yet validated or scrubbed. */
	PENDING,

	/** A valid image: EXIF/GPS stripped, cleaned copy and thumbnail written, safe to show. */
	READY,

	/** Not a valid image. Quarantined, never served. */
	REJECTED

}
