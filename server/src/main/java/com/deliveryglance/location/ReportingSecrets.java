package com.deliveryglance.location;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The secret a reporting browser proves it started the Location Sharing Session with. The session
 * cookie already says which Courier is calling; this says which page load is calling, so a report
 * cannot be replayed by a tab that no longer holds a live session.
 *
 * <p>Only the verifier is stored, so a database copy cannot be turned back into a working reporting
 * credential.
 */
final class ReportingSecrets {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int SECRET_BYTES = 32;

	private ReportingSecrets() {
	}

	static String issue() {
		byte[] secret = new byte[SECRET_BYTES];
		RANDOM.nextBytes(secret);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
	}

	static String verifierOf(String secret) {
		return HexFormat.of().formatHex(sha256().digest(secret.getBytes(StandardCharsets.UTF_8)));
	}

	/** Compares in constant time, so a wrong secret cannot be improved one character at a time. */
	static boolean matches(String secret, String verifier) {
		return MessageDigest.isEqual(verifierOf(secret).getBytes(StandardCharsets.US_ASCII),
				verifier.getBytes(StandardCharsets.US_ASCII));
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
