package com.deliveryglance.eta;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.eta.EtaRouteFacts.Route;
import com.deliveryglance.eta.EtaStore.StoredEta;
import com.deliveryglance.location.LocationFacts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules ADR 05 puts on a recalculation, tested where they live: keep a stale window when the
 * provider stumbles but the inputs are good, withdraw it the moment the Courier's location goes
 * unusable, hold a jittering window still, and only republish a real move. None of this needs a
 * database or a real provider — it is entirely a function of the last window, the current facts and
 * the clock.
 */
class EtaCalculationsTest {

	private static final UUID DELIVERY = UUID.randomUUID();

	private static final UUID COURIER = UUID.randomUUID();

	private static final GeoPoint COURIER_AT = new GeoPoint(51.5000, -0.1000);

	private static final GeoPoint PICKUP = new GeoPoint(51.5100, -0.1200);

	private static final GeoPoint HANDOFF = new GeoPoint(51.5200, -0.1300);

	private final FakeRoutes routes = new FakeRoutes();

	private final FakeLocations locations = new FakeLocations();

	private final FakeTravelTime provider = new FakeTravelTime();

	private final FakeStore store = new FakeStore();

	private final FakeChanges changes = new FakeChanges();

	private final MutableClock clock = new MutableClock(Instant.parse("2026-08-10T09:00:00Z"));

	private EtaCalculations calculations() {
		return new EtaCalculations(this.routes, this.locations, provider(this.provider), this.store, this.changes,
				this.clock);
	}

	@Test
	void drawsAndPublishesAWindowOnTheFirstSuccessfulEstimate() {
		this.routes.holds(inTransitRoute());
		this.locations.holds(COURIER_AT);
		this.provider.answers(Duration.ofMinutes(8));

		calculations().recalculate(DELIVERY);

		// In Transit, 8 minutes from 09:00 → point 09:08, five-minute half-width → outward [09:00, 09:15].
		assertThat(this.store.find(DELIVERY)).hasValueSatisfying((stored) -> {
			assertThat(stored.window().start()).isEqualTo(Instant.parse("2026-08-10T09:00:00Z"));
			assertThat(stored.window().end()).isEqualTo(Instant.parse("2026-08-10T09:15:00Z"));
			assertThat(stored.calculatedAt()).isEqualTo(Instant.parse("2026-08-10T09:00:00Z"));
		});
		assertThat(this.changes.changed).containsExactly(DELIVERY);
	}

	@Test
	void routesCourierThroughPickupToHandoffWhileAssigned() {
		this.routes.holds(assignedRoute());
		this.locations.holds(COURIER_AT);
		this.provider.answers(Duration.ofMinutes(10));

		calculations().recalculate(DELIVERY);

		assertThat(this.provider.lastWaypoints).containsExactly(COURIER_AT, PICKUP, HANDOFF);
	}

	@Test
	void keepsTheLastWindowWhenTheProviderFailsButLocationIsStillUsable() {
		givenAPublishedWindow();
		this.changes.changed.clear();

		// A minute later the provider gives nothing, but the Courier is still reporting a position.
		this.clock.set(Instant.parse("2026-08-10T09:01:00Z"));
		this.provider.answersNothing();
		calculations().recalculate(DELIVERY);

		// The window and its calculation time are untouched: the browser will show "last calculated X
		// ago" and withdraw it itself past five minutes. Nothing is republished.
		assertThat(this.store.find(DELIVERY)).hasValueSatisfying(
				(stored) -> assertThat(stored.calculatedAt()).isEqualTo(Instant.parse("2026-08-10T09:00:00Z")));
		assertThat(this.changes.changed).isEmpty();
	}

	@Test
	void withdrawsTheWindowAtOnceWhenTheCourierLocationIsUnavailable() {
		givenAPublishedWindow();
		this.changes.changed.clear();

		// The Courier stopped sharing: no usable position. ADR 05 withdraws the window immediately
		// rather than letting a now-unroutable estimate linger.
		this.clock.set(Instant.parse("2026-08-10T09:01:00Z"));
		this.locations.holds(null);
		calculations().recalculate(DELIVERY);

		assertThat(this.store.find(DELIVERY)).isEmpty();
		assertThat(this.changes.changed).containsExactly(DELIVERY);
	}

	@Test
	void withdrawsTheWindowWhenTheDeliveryLeavesEveryEtaBearingPhase() {
		givenAPublishedWindow();
		this.changes.changed.clear();

		this.routes.holds(null); // Delivered or cancelled — no phase carries an ETA.
		calculations().recalculate(DELIVERY);

		assertThat(this.store.find(DELIVERY)).isEmpty();
		assertThat(this.changes.changed).containsExactly(DELIVERY);
	}

	@Test
	void refreshesFreshnessButHoldsTheWindowStillWhenAnEstimateBarelyMoves() {
		givenAPublishedWindow(); // [09:00, 09:15] calculated at 09:00
		this.changes.changed.clear();

		// A minute later the estimate barely shifts — jitter. From 09:01, 7 minutes lands the point back
		// at 09:08, so outward rounding gives the same [09:00, 09:15]: neither endpoint moved. The
		// endpoints stay, but the calculation time advances so the window does not drift stale.
		this.clock.set(Instant.parse("2026-08-10T09:01:00Z"));
		this.provider.answers(Duration.ofMinutes(7)); // point 09:08 → raw [09:03,09:13] → outward [09:00,09:15]
		calculations().recalculate(DELIVERY);

		assertThat(this.store.find(DELIVERY)).hasValueSatisfying((stored) -> {
			assertThat(stored.window().start()).isEqualTo(Instant.parse("2026-08-10T09:00:00Z"));
			assertThat(stored.window().end()).isEqualTo(Instant.parse("2026-08-10T09:15:00Z"));
			assertThat(stored.calculatedAt()).isEqualTo(Instant.parse("2026-08-10T09:01:00Z"));
		});
		assertThat(this.changes.changed).isEmpty();
	}

	@Test
	void republishesWhenAnEndpointMovesAFullFiveMinutes() {
		givenAPublishedWindow(); // [09:00, 09:15]
		this.changes.changed.clear();

		this.clock.set(Instant.parse("2026-08-10T09:01:00Z"));
		this.provider.answers(Duration.ofMinutes(20)); // point 09:21 → outward [09:15, 09:30]
		calculations().recalculate(DELIVERY);

		assertThat(this.store.find(DELIVERY)).hasValueSatisfying((stored) -> {
			assertThat(stored.window().end()).isEqualTo(Instant.parse("2026-08-10T09:30:00Z"));
			assertThat(stored.calculatedAt()).isEqualTo(Instant.parse("2026-08-10T09:01:00Z"));
		});
		assertThat(this.changes.changed).containsExactly(DELIVERY);
	}

	@Test
	void computesNothingWhenNoProviderIsConfigured() {
		this.routes.holds(inTransitRoute());
		this.locations.holds(COURIER_AT);

		EtaCalculations withoutProvider = new EtaCalculations(this.routes, this.locations, provider(null), this.store,
				this.changes, this.clock);
		withoutProvider.recalculate(DELIVERY);

		assertThat(this.store.find(DELIVERY)).isEmpty();
		assertThat(this.changes.changed).isEmpty();
	}

	@Test
	void readsTheStoredWindowForTheRecipientView() {
		givenAPublishedWindow();

		assertThat(calculations().currentEta(DELIVERY)).hasValueSatisfying((snapshot) -> {
			assertThat(snapshot.windowStart()).isEqualTo(Instant.parse("2026-08-10T09:00:00Z"));
			assertThat(snapshot.windowEnd()).isEqualTo(Instant.parse("2026-08-10T09:15:00Z"));
			assertThat(snapshot.calculatedAt()).isEqualTo(Instant.parse("2026-08-10T09:00:00Z"));
		});
	}

	private void givenAPublishedWindow() {
		this.routes.holds(inTransitRoute());
		this.locations.holds(COURIER_AT);
		this.provider.answers(Duration.ofMinutes(8));
		calculations().recalculate(DELIVERY);
	}

	private static Route inTransitRoute() {
		return new Route(EtaPhase.IN_TRANSIT, PICKUP.latitude(), PICKUP.longitude(), HANDOFF.latitude(),
				HANDOFF.longitude(), COURIER);
	}

	private static Route assignedRoute() {
		return new Route(EtaPhase.ASSIGNED, PICKUP.latitude(), PICKUP.longitude(), HANDOFF.latitude(),
				HANDOFF.longitude(), COURIER);
	}

	private static ObjectProvider<TravelTimePort> provider(TravelTimePort port) {
		return new ObjectProvider<>() {
			@Override
			public TravelTimePort getObject() {
				if (port == null) {
					throw new NoSuchBeanDefinitionException(TravelTimePort.class);
				}
				return port;
			}

			@Override
			public TravelTimePort getObject(Object... args) {
				return getObject();
			}

			@Override
			public TravelTimePort getIfAvailable() {
				return port;
			}

			@Override
			public TravelTimePort getIfUnique() {
				return port;
			}
		};
	}

	private static final class FakeRoutes implements EtaRouteFacts {

		private Route route;

		void holds(Route held) {
			this.route = held;
		}

		@Override
		public Optional<Route> routeFor(UUID deliveryId) {
			assertThat(deliveryId).isEqualTo(DELIVERY);
			return Optional.ofNullable(this.route);
		}

		@Override
		public List<UUID> activeDeliveryIds() {
			return (this.route == null) ? List.of() : List.of(DELIVERY);
		}

	}

	private static final class FakeLocations implements LocationFacts {

		private DispatchPosition position;

		void holds(GeoPoint held) {
			this.position = (held == null) ? null : new DispatchPosition(held.latitude(), held.longitude());
		}

		@Override
		public Optional<DispatchPosition> positionForDispatch(UUID courierAccountId) {
			assertThat(courierAccountId).isEqualTo(COURIER);
			return Optional.ofNullable(this.position);
		}

		@Override
		public Optional<TrackedPosition> positionForTracking(UUID courierAccountId) {
			throw new AssertionError("ETA math must use the internal dispatch read, never the Recipient one");
		}

		@Override
		public CourierLocationFacts factsFor(UUID courierAccountId) {
			throw new AssertionError("ETA has no business reading Courier workspace status");
		}

	}

	private static final class FakeTravelTime implements TravelTimePort {

		private Optional<Duration> answer = Optional.empty();

		private List<GeoPoint> lastWaypoints = List.of();

		void answers(Duration duration) {
			this.answer = Optional.of(duration);
		}

		void answersNothing() {
			this.answer = Optional.empty();
		}

		@Override
		public Optional<Duration> travelTime(List<GeoPoint> waypoints) {
			this.lastWaypoints = List.copyOf(waypoints);
			return this.answer;
		}

	}

	private static final class FakeStore implements EtaStore {

		private final Map<UUID, StoredEta> rows = new HashMap<>();

		@Override
		public Optional<StoredEta> find(UUID deliveryId) {
			return Optional.ofNullable(this.rows.get(deliveryId));
		}

		@Override
		public void upsert(UUID deliveryId, EtaWindow window, Instant calculatedAt) {
			this.rows.put(deliveryId, new StoredEta(window, calculatedAt));
		}

		@Override
		public void delete(UUID deliveryId) {
			this.rows.remove(deliveryId);
		}

	}

	private static final class FakeChanges implements EtaChanges {

		private final List<UUID> changed = new ArrayList<>();

		@Override
		public void etaChanged(UUID deliveryId) {
			this.changed.add(deliveryId);
		}

	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void set(Instant instant) {
			this.now = instant;
		}

		@Override
		public Instant instant() {
			return this.now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
