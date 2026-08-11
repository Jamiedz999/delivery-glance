package com.deliveryglance.trackinglink;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.deliveryglance.shared.Secrets;

/**
 * Turns a link's identity into its capability. The token is not generated and stored; it is derived
 * on demand as {@code HMAC-SHA256(key[version], linkId:generation)}, which is what lets Copy return
 * the same link a second time although nothing kept a copy of the first.
 *
 * <p>The 256 bits of output are unpredictable without the key, so the link identity may be an
 * ordinary random UUID that appears in internal records; RFC 9562 is explicit that a UUID is not
 * itself a security capability, and here it is not being used as one.
 *
 * <p>Deriving is this class's own business. Storing and comparing the result is {@link Secrets},
 * the same handling every other credential in the application gets.
 */
final class TrackingCapabilities {

	private static final String ALGORITHM = "HmacSHA256";

	private final Map<Integer, byte[]> keysByVersion;

	private final int currentKeyVersion;

	TrackingCapabilities(Map<Integer, byte[]> keysByVersion, int currentKeyVersion) {
		this.keysByVersion = Map.copyOf(keysByVersion);
		this.currentKeyVersion = currentKeyVersion;
		if (!this.keysByVersion.containsKey(currentKeyVersion)) {
			throw new IllegalStateException(
					"Tracking key version " + currentKeyVersion + " is configured as current but has no key material");
		}
	}

	int currentKeyVersion() {
		return this.currentKeyVersion;
	}

	/**
	 * @throws IllegalStateException if the key that issued this link is no longer configured. The
	 * alternative — deriving under some other key — produces a token the stored verifier rejects,
	 * which surfaces as an unavailable link and hides an operational mistake as a Recipient problem.
	 */
	String derive(UUID linkId, int generation, int keyVersion) {
		byte[] key = this.keysByVersion.get(keyVersion);
		if (key == null) {
			throw new IllegalStateException("No tracking key configured for version " + keyVersion);
		}
		// The UUID's text form is fixed width, so no other (linkId, generation) pair can produce the
		// same input bytes and therefore the same capability.
		byte[] message = (linkId + ":" + generation).getBytes(StandardCharsets.UTF_8);
		return Secrets.encode(hmac(key, message));
	}

	private static byte[] hmac(byte[] key, byte[] message) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			return mac.doFinal(message);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HmacSHA256 is required by every Java platform", ex);
		}
	}

}
