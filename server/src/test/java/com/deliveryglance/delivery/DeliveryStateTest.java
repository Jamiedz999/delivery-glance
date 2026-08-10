package com.deliveryglance.delivery;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStateTest {

	@Test
	void definesEveryAllowedAndRejectedCoreTransition() {
		Map<DeliveryState, Set<DeliveryState>> allowed = Map.of(
				DeliveryState.AWAITING_COURIER, Set.of(DeliveryState.ASSIGNED, DeliveryState.CANCELLED),
				DeliveryState.ASSIGNED, Set.of(DeliveryState.IN_TRANSIT, DeliveryState.CANCELLED),
				DeliveryState.IN_TRANSIT, Set.of(DeliveryState.DELIVERED), DeliveryState.DELIVERED, Set.of(),
				DeliveryState.CANCELLED, Set.of());

		for (DeliveryState current : DeliveryState.values()) {
			for (DeliveryState requested : DeliveryState.values()) {
				assertThat(current.canTransitionTo(requested))
					.as("%s -> %s", current, requested)
					.isEqualTo(allowed.get(current).contains(requested));
			}
		}
	}

}
