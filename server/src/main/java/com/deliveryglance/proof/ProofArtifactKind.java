package com.deliveryglance.proof;

/**
 * The two artifacts a Courier captures at handoff. Both travel the same path — direct upload to S3,
 * asynchronous validation, EXIF scrub and thumbnail — so the kind is only ever a segment of an
 * object key and a label on a stored row, never a branch in the storage code.
 */
enum ProofArtifactKind {

	PHOTO,

	SIGNATURE;

	/** The lowercase segment this kind occupies in an object key. */
	String segment() {
		return name().toLowerCase();
	}

}
