package com.deliveryglance.identityaccess;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SecurityContextCurrentActorProvider implements CurrentActorProvider {

	@Override
	public CurrentActor requireCurrentActor() {
		return currentActorOf(SecurityContextHolder.getContext().getAuthentication())
			.orElseThrow(() -> new IllegalStateException("No Internal Account session is bound to the current request"));
	}

	@Override
	public Optional<CurrentActor> currentActorOf(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof InternalAccountPrincipal principal)) {
			return Optional.empty();
		}
		return Optional.of(principal.toCurrentActor());
	}

}
