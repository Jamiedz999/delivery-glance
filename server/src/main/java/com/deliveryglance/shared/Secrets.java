package com.deliveryglance.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The handling every credential in this application shares: issue it from a CSPRNG, store only a
 * digest of it, and compare in constant time.
 *
 * <p>Two modules now hold secrets a browser presents back — a Courier's reporting secret and a
 * Tracking capability and its grant — and both want exactly these three operations. They are
 * primitives, not a business service: nothing here knows what a Courier or a Tracking Link is, and
 * each module keeps its own policy about what its secret means and how long it lives.
 */
public final class Secrets {

	private static final SecureRandom RANDOM = new SecureRandom();

	/** 256 bits. A clickable link costs nobody any typing, so there is no reason to go shorter. */
	private static final int SECRET_BYTES = 32;

	private Secrets() {
	}

	/** A fresh unguessable value, base64url so it is safe in a URL fragment and in a cookie. */
	public static String issue() {
		byte[] secret = new byte[SECRET_BYTES];
		RANDOM.nextBytes(secret);
		return encode(secret);
	}

	public static String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	/** The value stored in place of the secret. SHA-256 keeps a high-entropy secret's full
	 * guessing strength, and cannot be run backwards to produce a working credential. */
	public static String verifierOf(String secret) {
		return HexFormat.of().formatHex(sha256().digest(secret.getBytes(StandardCharsets.UTF_8)));
	}

	/** Compares in constant time, so a wrong secret cannot be improved one character at a time. */
	public static boolean matches(String secret, String verifier) {
		return MessageDigest.isEqual(verifierOf(secret).getBytes(StandardCharsets.US_ASCII),
				verifier.getBytes(StandardCharsets.US_ASCII));
	}

	public static byte[] digest(String value) {
		return sha256().digest(value.getBytes(StandardCharsets.UTF_8));
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
