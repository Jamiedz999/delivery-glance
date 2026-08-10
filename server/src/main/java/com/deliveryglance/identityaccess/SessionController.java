package com.deliveryglance.identityaccess;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in and sign-out are handled by the security filter chain ({@code POST} and {@code DELETE} on
 * {@code /api/session}); this controller only answers "who am I". Sign-in deliberately has no
 * controller method, so credentials never reach application code.
 */
@RestController
class SessionController {

	private final CurrentActorProvider currentActorProvider;

	SessionController(CurrentActorProvider currentActorProvider) {
		this.currentActorProvider = currentActorProvider;
	}

	@GetMapping("/api/session")
	SessionView currentSession() {
		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		return new SessionView(actor.displayName(), actor.role());
	}

	/**
	 * Deliberately excludes the account identifier and email: the browser only needs to greet the
	 * signed-in person and pick a workspace.
	 */
	record SessionView(String displayName, InternalAccountRole role) {
	}

}
