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

}
