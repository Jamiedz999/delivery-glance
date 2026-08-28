package com.deliveryglance.eta;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.eta.EtaRouteFacts.Route;
import com.deliveryglance.eta.EtaStore.StoredEta;
import com.deliveryglance.location.LocationFacts;
import com.deliveryglance.location.LocationFacts.DispatchPosition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The heart of the module: turn one Delivery's current facts into a window, or into the honest
 * absence of one. Every rule ADR 05 puts on the ETA is decided here — which inputs make a window
 * possible, when a provider fault keeps the last one and when a lost location withdraws it, and when
 * a fresh answer has moved the window enough to republish.
 *
 * <p>A recalculation never runs inside the transaction that triggered it. The immediate trigger
 * defers to after-commit, and the sweeper has no transaction at all, so the provider call — the one
 * slow, failure-prone step — happens with no Delivery row locked and no assignment held. A provider
 * that times out delays a window; it can never delay or roll back a transition.
 */
@Service
class EtaCalculations implements EtaProjection, EtaRecalculation {

	private static final Logger logger = LoggerFactory.getLogger(EtaCalculations.class);

	private final EtaRouteFacts routes;

	private final LocationFacts locations;

	private final ObjectProvider<TravelTimePort> travelTime;

	private final EtaStore repository;

	private final EtaChanges changes;

	private final Clock clock;

	EtaCalculations(EtaRouteFacts routes, LocationFacts locations, ObjectProvider<TravelTimePort> travelTime,
			EtaStore repository, EtaChanges changes, Clock clock) {
		this.routes = routes;
		this.locations = locations;
		this.travelTime = travelTime;
		this.repository = repository;
		this.changes = changes;
		this.clock = clock;
	}

	@Override
	public Optional<EtaSnapshot> currentEta(UUID deliveryId) {
		return this.repository.find(deliveryId)
			.map((stored) -> new EtaSnapshot(stored.window().start(), stored.window().end(), stored.calculatedAt()));
	}

	@Override
	public void deliveryPhaseChanged(UUID deliveryId) {
		// After the transition commits, never during it: the provider call must not run while the
		// Delivery row that triggered this is still locked.
		afterCommit(() -> recalculate(deliveryId));
	}

	/**
	 * Recomputes the window for one Delivery from its current facts. Safe to call with no ambient
	 * transaction — the sweeper does — because it opens none and holds no lock across the provider
	 * call. Swallows its own failures: an ETA that cannot be computed is a missing window, never a
	 * raised exception, so a sweep pass or an after-commit hook is never derailed by one Delivery.
	 */
	void recalculate(UUID deliveryId) {
		try {
			attemptRecalculate(deliveryId);
		}
		catch (RuntimeException ex) {
			logger.warn("Could not recalculate ETA for delivery {}; its last window stands until it ages out",
					deliveryId, ex);
		}
	}

	private void attemptRecalculate(UUID deliveryId) {
		Optional<Route> maybeRoute = this.routes.routeFor(deliveryId);
		if (maybeRoute.isEmpty()) {
			// Gone, still awaiting a Courier, or terminal — no phase carries an ETA, so drop any window.
			withdraw(deliveryId);
			return;
		}
		Route route = maybeRoute.get();
		Optional<GeoPoint> origin = originFor(route);
		if (origin.isEmpty()) {
			// No usable Courier location. ADR 05: a lost location withdraws the window at once rather
			// than letting a now-unroutable estimate linger.
			withdraw(deliveryId);
			return;
		}

		TravelTimePort provider = this.travelTime.getIfAvailable();
		if (provider == null) {
			// No provider configured: the feature is off. Any existing window simply ages out; there is
			// nothing to recompute it from.
			return;
		}
		Optional<Duration> travel = provider.travelTime(route.waypointsFrom(origin.get()));
		if (travel.isEmpty()) {
			// Inputs are usable but the provider gave no answer. Keep the last window: the browser shows
			// "last calculated X ago" and withdraws it itself once it passes five minutes.
			return;
		}

		store(deliveryId, EtaWindows.from(this.clock.instant(), travel.get(), route.phase()));
	}

	private Optional<GeoPoint> originFor(Route route) {
		if (route.courierAccountId() == null) {
			return Optional.empty();
		}
		return this.locations.positionForDispatch(route.courierAccountId())
			.map((position) -> new GeoPoint(position.latitude(), position.longitude()));
	}

	private void store(UUID deliveryId, EtaWindow window) {
		Instant now = this.clock.instant();
		Optional<StoredEta> stored = this.repository.find(deliveryId);
		if (stored.isPresent() && !EtaWindows.moved(stored.get().window(), window)) {
			// A successful recalculation that did not move the window: keep the published endpoints so
			// the page does not twitch, but advance freshness so it does not drift stale.
			this.repository.upsert(deliveryId, stored.get().window(), now);
			return;
		}
		this.repository.upsert(deliveryId, window, now);
		this.changes.etaChanged(deliveryId);
	}

	private void withdraw(UUID deliveryId) {
		if (this.repository.find(deliveryId).isPresent()) {
			this.repository.delete(deliveryId);
			this.changes.etaChanged(deliveryId);
		}
	}

	private void afterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

	List<UUID> activeDeliveryIds() {
		return this.routes.activeDeliveryIds();
	}

}
