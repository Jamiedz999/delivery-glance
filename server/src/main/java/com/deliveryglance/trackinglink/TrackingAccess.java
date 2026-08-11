package com.deliveryglance.trackinglink;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

/**
 * Turns the cookie on a request into the Delivery it authorizes.
 *
 * <p>It is the whole of what leaves this module to the Recipient view. Keeping it here rather than
 * letting recipientview read the cookie itself is what makes "a grant carries no authority anywhere
 * else in the API" a property of one class instead of a convention several packages have to keep.
 */
@Component
class TrackingAccess implements LinkHolderAuthorization {

	private final TrackingLinks links;

	private final TrackingGrants grants;

	TrackingAccess(TrackingLinks links, TrackingGrants grants) {
		this.links = links;
		this.grants = grants;
	}

	@Override
	public UUID requireAuthorizedDelivery(HttpServletRequest request, HttpServletResponse response) {
		String secret = this.grants.presentedSecret(request).orElseThrow(UnavailableLinkException::new);
		try {
			return this.links.authorizedDeliveryFor(secret);
		}
		catch (UnavailableLinkException ex) {
			// The cookie is no longer worth anything, so the browser stops sending it rather than
			// retrying with it on every reconnect.
			this.grants.clear(response);
			throw ex;
		}
	}

}
