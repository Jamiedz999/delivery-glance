package com.deliveryglance.location;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Start, report and Stop for one Courier's Location Sharing. The sharing intent is durable; the
 * position it produces is not, and this class is where those two lifetimes are kept in step.
 */
@Service
class LocationSharing implements LocationFacts {

	private final LocationSharingRepository repository;

	private final LatestLocationStore store;

	private final Clock clock;

	LocationSharing(LocationSharingRepository repository, LatestLocationStore store, Clock clock) {
		this.repository = repository;
		this.store = store;
		this.clock = clock;
	}

	@Transactional
	LocationViews.StartedSession start(UUID courierAccountId) {
		UUID generation = UUID.randomUUID();
		String reportingSecret = ReportingSecrets.issue();
		Instant startedAt = this.clock.instant();

		this.repository.save(courierAccountId, generation, ReportingSecrets.verifierOf(reportingSecret), startedAt);
		// After the new generation is stored, not before: a report that was validated against the
		// old one is then already speaking for a session the database no longer knows.
		this.store.forget(courierAccountId);

		return new LocationViews.StartedSession(generation, reportingSecret, startedAt);
	}

	/** Ends sharing and removes the coordinates now. Ending a session that is not running is fine. */
	@Transactional
	void stop(UUID courierAccountId) {
		this.repository.delete(courierAccountId);
		this.store.forget(courierAccountId);
	}

	@Transactional(readOnly = true)
	LocationViews.Report report(UUID courierAccountId, LocationRequests.Report request) {
		LocationSharingRepository.CurrentSession session = this.repository.find(courierAccountId)
			.filter((current) -> current.generation().equals(request.generation()))
			.filter((current) -> ReportingSecrets.matches(request.reportingSecret(),
					current.reportingSecretVerifier()))
			// A wrong generation and a wrong secret are answered identically, so a caller cannot
			// learn which half it got right.
			.orElseThrow(LocationSharingEndedException::new);

		ReportOutcome outcome = this.store.record(new LocationReport(courierAccountId, session.generation(),
				request.longitude(), request.latitude(), request.accuracyMetres(), request.recordedAt()));

		return new LocationViews.Report(outcome, status(courierAccountId));
	}

	@Override
	@Transactional(readOnly = true)
	public CourierLocationFacts factsFor(UUID courierAccountId) {
		return new CourierLocationFacts(this.repository.find(courierAccountId)
			.map(LocationSharingRepository.CurrentSession::startedAt)
			.orElse(null), status(courierAccountId));
	}

	@Override
	public Optional<DispatchPosition> positionForDispatch(UUID courierAccountId) {
		// No freshness test here or in dispatch: current() holds the usable limit for every reader
		// by deleting an expired snapshot rather than returning one, so the rule stays in one place.
		return this.store.current(courierAccountId)
			.map((snapshot) -> new DispatchPosition(snapshot.latitude(), snapshot.longitude()));
	}

	private LocationStatus status(UUID courierAccountId) {
		Optional<LatestLocation> current = this.store.current(courierAccountId);
		if (current.isEmpty()) {
			return LocationStatus.unavailable();
		}
		LatestLocation snapshot = current.get();
		return new LocationStatus(LocationFreshness.of(snapshot.recordedAt(), this.clock.instant()),
				snapshot.recordedAt(), snapshot.accuracyMetres());
	}

}
