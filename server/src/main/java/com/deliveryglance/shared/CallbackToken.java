package com.deliveryglance.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Whether a server-to-server callback presented the shared bearer token a deployment configured for
 * it. It is a credential primitive in the sense {@link Secrets} is: it knows nothing about what the
 * token authorizes — a proof-processed callback, a notification dispatch — only how to compare a
 * presented one against the expected one without leaking how close a wrong guess was.
 *
 * <p>It arrived when a second module needed exactly this: an asynchronous collaborator (a Lambda)
 * that authenticates back into the application by a token rather than a session. The first such
 * caller compared it inline; the second is what moves the comparison here rather than copying it.
 */
public final class CallbackToken {

	private static final String BEARER_PREFIX = "Bearer ";

	private CallbackToken() {
	}

	/**
	 * @param authorizationHeader the request's {@code Authorization} header, or null if it carried
	 * none
	 * @param expectedToken the token this deployment configured for the callback, or blank/null if
	 * it configured none
	 * @return true only when a token is configured and the header presents exactly it. False when no
	 * token is configured — so a deployment without the collaborator refuses every callback — and
	 * when the header is absent, not a bearer token, or does not match. The comparison is
	 * constant-time, so a wrong token cannot be improved one character at a time.
	 */
	public static boolean authorizes(String authorizationHeader, String expectedToken) {
		if (expectedToken == null || expectedToken.isBlank()) {
			return false;
		}
		String presented = bearerToken(authorizationHeader);
		if (presented == null) {
			return false;
		}
		return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
				presented.getBytes(StandardCharsets.UTF_8));
	}

	private static String bearerToken(String authorizationHeader) {
		if (authorizationHeader == null
				|| !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return null;
		}
		return authorizationHeader.substring(BEARER_PREFIX.length()).strip();
	}

}
