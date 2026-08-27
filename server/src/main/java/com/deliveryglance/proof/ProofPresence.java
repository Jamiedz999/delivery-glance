package com.deliveryglance.proof;

import java.util.UUID;

/**
 * The one thing the Recipient view asks of proof, and the whole of what the privacy decision lets a
 * Recipient learn: whether a Delivery was confirmed with proof on file. It is a yes or no — never a
 * key, a URL, a thumbnail or a time — so the Recipient surface cannot accidentally grow a way to
 * reach an image the Delivery Team alone is meant to see.
 */
public interface ProofPresence {

	/**
	 * Whether this Delivery has proof on file: at least one captured artifact that has been
	 * validated and scrubbed (READY). A still-processing upload does not count — it may yet be
	 * quarantined as not an image — so the Recipient is never told proof exists for one that never
	 * comes to be.
	 */
	boolean hasProofOnFile(UUID deliveryId);

}
