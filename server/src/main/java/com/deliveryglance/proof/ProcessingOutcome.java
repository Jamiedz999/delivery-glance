package com.deliveryglance.proof;

/** What the processing Lambda decided about one uploaded artifact. */
enum ProcessingOutcome {

	/** A valid image the Lambda scrubbed and thumbnailed; it carries the resulting keys and hash. */
	READY,

	/** Not a valid image; the Lambda quarantined it and carries no keys. */
	REJECTED

}
