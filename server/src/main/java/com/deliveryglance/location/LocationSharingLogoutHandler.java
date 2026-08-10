package com.deliveryglance.location;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.deliveryglance.identityaccess.CurrentActor;
import com.deliveryglance.identityaccess.CurrentActorProvider;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Signing out ends Location Sharing exactly as pressing Stop does. Without this, a Courier who
 * signed out would keep a usable position on the server for up to two more minutes, which is not
 * what "I have finished" means to the person pressing it.
 */
@Component
class LocationSharingLogoutHandler implements LogoutHandler {

	private final LocationSharing locationSharing;

	private final CurrentActorProvider currentActorProvider;

	LocationSharingLogoutHandler(LocationSharing locationSharing, CurrentActorProvider currentActorProvider) {
		this.locationSharing = locationSharing;
		this.currentActorProvider = currentActorProvider;
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		// The Authentication being logged out is read directly: by the time the handlers run, the
		// security context may already have been cleared by another handler in the same chain.
		this.currentActorProvider.currentActorOf(authentication)
			.map(CurrentActor::accountId)
			.ifPresent(this.locationSharing::stop);
	}

}
