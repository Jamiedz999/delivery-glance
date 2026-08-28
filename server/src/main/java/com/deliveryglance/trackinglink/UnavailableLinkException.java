package com.deliveryglance.trackinglink;

/**
 * The one refusal a Link Holder can ever see. Unknown, malformed, expired and revoked capabilities
 * all raise this, and it carries no reason, because a reason is an oracle: "expired" tells a guesser
 * the Delivery existed, and RFC 7662 makes the same point about token introspection.
 *
 * <p>It is thrown without a stack trace. The trace would say which branch refused the request, and
 * that is exactly the distinction this exception exists to erase from anything that might be logged.
 *
 * <p>Public because recipientview answers the same audience and must answer it identically. Its
 * status and wording stay owned here, in one place, for the same reason there is one exception.
 */
public class UnavailableLinkException extends RuntimeException {

	public UnavailableLinkException() {
		super("This tracking link is no longer available.", null, false, false);
	}

}
