package com.deliveryglance.proof;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The one place that knows how a proof object is named, so every prefix a bucket policy or Lambda
 * trigger depends on is readable together.
 *
 * <p>Four prefixes, and the boundary between them is a security boundary, not a convenience:
 *
 * <ul>
 * <li>{@code raw/} — exactly what the browser uploaded, the only prefix a Courier can write to and
 * the only one the Lambda reads. Never served to anyone.</li>
 * <li>{@code clean/} — the full image after EXIF/GPS has been stripped. What a Dispatcher sees when
 * they open the full proof.</li>
 * <li>{@code thumb/} — the thumbnail written alongside {@code clean/}.</li>
 * <li>{@code quarantine/} — where the Lambda moves anything that is not a valid image, so an
 * invalid upload is kept out of every read path rather than deleted silently.</li>
 * </ul>
 *
 * <p>Every key carries its Delivery id and artifact kind, so a key submitted with a handoff command
 * can be checked against the Delivery it claims to belong to without a database round trip: a
 * Courier who forges a key for another Delivery forges a prefix this class will reject.
 */
final class ProofObjectKeys {

	static final String RAW_PREFIX = "raw/";

	static final String CLEAN_PREFIX = "clean/";

	static final String THUMBNAIL_PREFIX = "thumb/";

	static final String QUARANTINE_PREFIX = "quarantine/";

	private static final Pattern RAW_KEY = Pattern
		.compile("raw/deliveries/([0-9a-f-]{36})/(photo|signature)/([0-9a-f-]{36})");

	private ProofObjectKeys() {
	}

	/** A fresh, unguessable raw key for one captured artifact of one Delivery. */
	static String newRawKey(UUID deliveryId, ProofArtifactKind kind) {
		return RAW_PREFIX + "deliveries/" + deliveryId + "/" + kind.segment() + "/" + UUID.randomUUID();
	}

	/**
	 * Reads a raw key the client submitted back, confirming it is a well-formed raw key that names
	 * this Delivery. Anything else — a different Delivery, a non-raw prefix, a malformed key — is
	 * refused, because the only keys the application will record are ones it minted for this handoff.
	 */
	static Optional<ParsedRawKey> parseRawKeyFor(UUID deliveryId, String objectKey) {
		if (objectKey == null) {
			return Optional.empty();
		}
		var matcher = RAW_KEY.matcher(objectKey);
		if (!matcher.matches() || !matcher.group(1).equals(deliveryId.toString())) {
			return Optional.empty();
		}
		return Optional.of(new ParsedRawKey(objectKey, ProofArtifactKind.valueOf(matcher.group(2).toUpperCase())));
	}

	record ParsedRawKey(String objectKey, ProofArtifactKind kind) {
	}

}
