package com.deliveryglance.identityaccess;

import java.util.Optional;

import org.springframework.security.core.Authentication;

/**
 * The one thing peer modules ask of {@code identityaccess}: who is acting right now. Authorization
 * itself stays in the security policy, so callers can assume the request was already allowed.
 */
public interface CurrentActorProvider {

	/**
	 * @throws IllegalStateException if no Internal Account session is bound to the current request,
	 * which means a route was reached without the security policy that should protect it
	 */
	CurrentActor requireCurrentActor();

	/**
	 * Who a specific authentication belongs to. Sign-out needs this: by the time a logout handler
	 * runs, the security context may already have been cleared, and the ending session is handed to
	 * it directly instead.
	 *
	 * @return empty when the authentication is not an Internal Account session
	 */
	Optional<CurrentActor> currentActorOf(Authentication authentication);

}
