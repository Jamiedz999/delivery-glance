package com.deliveryglance.identityaccess;

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

}
