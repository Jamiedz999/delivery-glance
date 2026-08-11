package com.deliveryglance.trackinglink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Turns a link's identity into its capability. The token is not generated and stored; it is derived
 * on demand as {@code HMAC-SHA256(key[version], linkId:generation)}, which is what lets Copy return
 * the same link a second time although nothing kept a copy of the first.
 *
 * <p>The 256 bits of output are unpredictable without the key, so the link identity may be an
 * ordinary random UUID that appears in internal records; RFC 9562 is explicit that a UUID is not
 * itself a security capability, and here it is not being used as one.
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
		return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, message));
	}

	static String verifierOf(String token) {
		return HexFormat.of().formatHex(sha256().digest(token.getBytes(StandardCharsets.UTF_8)));
	}

	/** Compares in constant time, so a wrong token cannot be improved one character at a time. */
	static boolean matches(String token, String verifier) {
		return MessageDigest.isEqual(verifierOf(token).getBytes(StandardCharsets.US_ASCII),
				verifier.getBytes(StandardCharsets.US_ASCII));
	}

	private static byte[] hmac(byte[] key, byte[] message) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			return mac.doFinal(message);
		}
		catch (NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
			throw new IllegalStateException("HmacSHA256 is required by every Java platform", ex);
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
		}
	}

}
