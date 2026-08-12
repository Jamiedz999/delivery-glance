package com.deliveryglance.demo;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The demo's one control. Dispatcher-only and CSRF-protected like every other unsafe internal
 * route, and mapped at all only when {@code delivery-glance.demo.reset-enabled} is on.
 *
 * <p>On a public demo whose credentials are published in the README, "Dispatcher-only" does not make
 * this private, and it is not meant to: every row it touches is fictional and the demo is there to
 * be driven. What the role check does is keep the reset out of the Courier's workspace, where a
 * mis-tap during a recorded walkthrough would erase the Delivery being recorded.
 *
 * <p>It carries the switch itself rather than being declared by {@link DemoConfig}, because
 * {@code @RestController} is a {@code @Component} and component scanning would otherwise build it
 * whatever that class decided. Both read the one constant, so they cannot name different properties;
 * and if they ever did disagree, this controller would fail to find its {@link DemoReset} and the
 * application would refuse to start rather than serve a half-built demo.
 */
@RestController
@ConditionalOnProperty(DemoConfig.RESET_ENABLED)
class DemoResetController {

	private final DemoReset demoReset;

	DemoResetController(DemoReset demoReset) {
		this.demoReset = demoReset;
	}

	@PostMapping("/api/demo/reset")
	Result reset() {
		return new Result(this.demoReset.reset());
	}

	/**
	 * @param createdReferences what the demo now holds, in walkthrough order.
	 */
	record Result(List<String> createdReferences) {
	}

}
