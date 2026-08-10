package com.deliveryglance.identityaccess;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SecurityContextCurrentActorProvider implements CurrentActorProvider {

	@Override
	public CurrentActor requireCurrentActor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof InternalAccountPrincipal principal)) {
			throw new IllegalStateException("No Internal Account session is bound to the current request");
		}
		return principal.toCurrentActor();
	}

}
