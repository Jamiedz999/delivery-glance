package com.deliveryglance.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Keeps the public demo showing the state its walkthrough starts from, without anybody pressing
 * anything. A demo whose credentials are published gets driven by strangers: a Delivery is assigned,
 * a Courier is left On Duty, a Tracking Link is handed out, and the next visitor arrives at somebody
 * else's half-finished walkthrough. This puts it back.
 *
 * <p>It runs the same {@link DemoReset} the Dispatcher's route runs — the fictional Deliveries have
 * one provisioning path and this is not a second one. What it adds is when: once when the
 * application is ready, so a fresh deployment shows the demo immediately rather than an empty board
 * until the first scheduled run, and then on the cron the deployment configured.
 *
 * <p>Exactly one instance runs, ever ({@code infra/terraform/core/compute.tf}), so two boxes cannot
 * reset each other's demo mid-walkthrough and no distributed lock is needed.
 *
 * <p>A failed reset is logged and swallowed rather than thrown. On the startup pass that is the
 * difference between a demo that is briefly empty and an application that refuses to start; on a
 * scheduled pass the demo simply keeps what it had until the next tick. Either way the reset is one
 * transaction, so what is left behind is a whole demo rather than half of one.
 */
class DemoSelfHeal {

	private static final Logger logger = LoggerFactory.getLogger(DemoSelfHeal.class);

	private final DemoReset demoReset;

	private final UserDetailsService accounts;

	private final String dispatcherEmail;

	DemoSelfHeal(DemoReset demoReset, UserDetailsService accounts, String dispatcherEmail) {
		this.demoReset = demoReset;
		this.accounts = accounts;
		this.dispatcherEmail = dispatcherEmail;
	}

	/**
	 * Both triggers call this one method, because "the demo has drifted" and "the demo has just been
	 * deployed" want exactly the same thing done about them.
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Scheduled(cron = "${" + DemoConfig.RESET_SCHEDULE + "}")
	void restoreTheWalkthroughState() {
		try {
			actAsTheDispatcher();
			logger.info("Demo self-heal restored {}", this.demoReset.reset());
		}
		catch (RuntimeException ex) {
			logger.error("Demo self-heal failed; the demo keeps the state it had", ex);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/**
	 * A Delivery records who made it, so {@code DeliveryProvisioning} requires a Staff Account to
	 * be acting — and on a timer there is no request and no session to take one from. Rather than
	 * give the demo a second, unattributed way to create a Delivery, the reset is run as the same
	 * Dispatcher who would have pressed the button, whose row the sign-in path loads by the same
	 * service. The context is this thread's alone and is cleared as soon as the reset returns.
	 *
	 * <p>This grants nothing over HTTP. No request is in flight, Spring Security binds the context to
	 * a request of its own on every one that is, and {@code POST /api/demo/reset} keeps every refusal
	 * it had.
	 */
	private void actAsTheDispatcher() {
		UserDetails dispatcher = this.accounts.loadUserByUsername(this.dispatcherEmail);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(dispatcher, null, dispatcher.getAuthorities()));
		SecurityContextHolder.setContext(context);
	}

}
