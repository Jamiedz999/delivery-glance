package com.deliveryglance.trackinglink;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.deliveryglance.shared.Secrets;

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
		return authorize(request, response).deliveryId();
	}

	@Override
	public HeldGrant requireHeldGrant(HttpServletRequest request, HttpServletResponse response) {
		return authorize(request, response);
	}

	private HeldGrant authorize(HttpServletRequest request, HttpServletResponse response) {
		String secret = this.grants.presentedSecret(request).orElseThrow(UnavailableLinkException::new);
		// The verifier, not the secret. It is what the grant table already stores and what the
		// recheck below looks up by, so a connection that lives for minutes holds a value that
		// could not be presented as a cookie even if it escaped.
		String verifier = Secrets.verifierOf(secret);
		try {
			return new StoredGrantHeld(verifier, this.links.authorizedDeliveryForVerifier(verifier));
		}
		catch (UnavailableLinkException ex) {
			// The cookie is no longer worth anything, so the browser stops sending it rather than
			// retrying with it on every reconnect.
			this.grants.clear(response);
			throw ex;
		}
	}

	/**
	 * The Delivery is remembered rather than reread on every recheck. It cannot change: a grant is
	 * scoped to one link and a link to one Delivery, so the only thing a recheck can discover is
	 * that the grant has stopped authorizing anything at all.
	 */
	private final class StoredGrantHeld implements HeldGrant {

		private final String secretVerifier;

		private final UUID deliveryId;

		private StoredGrantHeld(String secretVerifier, UUID deliveryId) {
			this.secretVerifier = secretVerifier;
			this.deliveryId = deliveryId;
		}

		@Override
		public UUID deliveryId() {
			return this.deliveryId;
		}

		@Override
		public boolean stillAuthorizes() {
			try {
				TrackingAccess.this.links.authorizedDeliveryForVerifier(this.secretVerifier);
				return true;
			}
			catch (UnavailableLinkException ex) {
				return false;
			}
		}

	}

}
