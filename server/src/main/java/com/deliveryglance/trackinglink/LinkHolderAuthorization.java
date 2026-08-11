package com.deliveryglance.trackinglink;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * How a peer module authorizes a Link Holder without learning how a grant is carried.
 *
 * <p>It answers with a Delivery identifier and nothing else. That is the whole of what a Recipient
 * route needs: ADR 06 says the capability authorizes reading one Delivery, so "which Delivery" is
 * the only fact the grant produces, and what may then be said about it is the reader's business
 * rather than this module's.
 *
 * <p>The servlet types are in the signature because the grant lives in a cookie and a spent one has
 * to be cleared from the response. Handing the caller a cookie name to read, or a secret to pass
 * back, would move exactly the parts of this that must not be copied.
 */
public interface LinkHolderAuthorization {

	/**
	 * @return the one Delivery the grant presented by this request authorizes reading
	 * @throws UnavailableLinkException if no usable grant is presented, with nothing to say which of
	 * absent, unknown and expired it was. A spent cookie is cleared from the response on the way out
	 * so the browser stops sending it.
	 */
	UUID requireAuthorizedDelivery(HttpServletRequest request, HttpServletResponse response);

	/**
	 * The same authorization, in a form a connection that outlives its request can ask again.
	 *
	 * <p>A one-shot answer is enough for a read that finishes in milliseconds and wrong for a stream
	 * that stays open for minutes: the grant may expire, or the Delivery may end and shorten the
	 * link's effective expiry, while nothing is arriving to notice. The alternative — handing the
	 * caller the cookie value so it can re-present it — is the one thing {@link
	 * LinkHolderAuthorization} exists to prevent, so the recheck stays inside this module and the
	 * caller holds an object rather than a secret.
	 *
	 * @throws UnavailableLinkException on the same terms as {@link #requireAuthorizedDelivery}
	 */
	HeldGrant requireHeldGrant(HttpServletRequest request, HttpServletResponse response);

	/**
	 * A grant a long-lived connection keeps hold of. It answers the two questions such a connection
	 * has — which Delivery, and may I still — and carries nothing that could be presented anywhere
	 * else.
	 */
	interface HeldGrant {

		UUID deliveryId();

		/**
		 * Whether this grant still authorizes reading its Delivery, rechecked against the clock,
		 * the link's generation and the Delivery's own terminal grace period.
		 *
		 * @return false rather than throwing, because the caller's response to "no longer" is to
		 * close a stream quietly rather than to explain anything
		 */
		boolean stillAuthorizes();

	}

}
