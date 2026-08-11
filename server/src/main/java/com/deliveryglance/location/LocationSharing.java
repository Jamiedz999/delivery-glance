package com.deliveryglance.location;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.recipientview.RecipientViewUpdates;
import com.deliveryglance.shared.Secrets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Start, report and Stop for one Courier's Location Sharing. The sharing intent is durable; the
 * position it produces is not, and this class is where those two lifetimes are kept in step.
 *
 * <p>The reporting secret this issues names the page load, not the Courier — the session cookie
 * already says who is calling. Only its verifier is stored, so a database copy cannot be turned back
 * into a working reporting credential.
 */
@Service
class LocationSharing implements LocationFacts {

	private final LocationSharingRepository repository;

	private final LatestLocationStore store;

	private final RecipientViewUpdates recipientViews;

	private final Clock clock;

	LocationSharing(LocationSharingRepository repository, LatestLocationStore store,
			RecipientViewUpdates recipientViews, Clock clock) {
		this.repository = repository;
		this.store = store;
		this.recipientViews = recipientViews;
		this.clock = clock;
	}

	@Transactional
	LocationViews.StartedSession start(UUID courierAccountId) {
		UUID generation = UUID.randomUUID();
		String reportingSecret = Secrets.issue();
		Instant startedAt = this.clock.instant();

		this.repository.save(courierAccountId, generation, Secrets.verifierOf(reportingSecret), startedAt);
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
			.filter((current) -> Secrets.matches(request.reportingSecret(),
					current.reportingSecretVerifier()))
			// A wrong generation and a wrong secret are answered identically, so a caller cannot
			// learn which half it got right.
			.orElseThrow(LocationSharingEndedException::new);

		ReportOutcome outcome = this.store.record(new LocationReport(courierAccountId, session.generation(),
				request.longitude(), request.latitude(), request.accuracyMetres(), request.recordedAt()));

		// Only an accepted reading moved Current Location. A duplicate, a late arrival or a poor fix
		// left the snapshot exactly as it was, so there is nothing for a Recipient page to refetch —
		// and telling it otherwise would describe a Courier still reporting, which is a fact about
		// them rather than about the Delivery. Which Delivery this is, if any, is not asked here:
		// this module knows about Couriers, and joining one to a Delivery is somebody else's read.
		if (outcome == ReportOutcome.ACCEPTED) {
			this.recipientViews.courierPositionChanged(courierAccountId);
		}

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

	@Override
	public Optional<TrackedPosition> positionForTracking(UUID courierAccountId) {
		// Same read and the same usable limit as dispatch gets; the difference is entirely in what
		// the caller is allowed to do with it, which is why the two are not one method.
		return this.store.current(courierAccountId)
			.map((snapshot) -> new TrackedPosition(snapshot.latitude(), snapshot.longitude(),
					snapshot.accuracyMetres(), snapshot.recordedAt()));
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
