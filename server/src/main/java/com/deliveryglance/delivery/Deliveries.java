package com.deliveryglance.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.identityaccess.CurrentActor;
import com.deliveryglance.identityaccess.CurrentActorProvider;
import com.deliveryglance.notification.NotificationOutbox;
import com.deliveryglance.proof.DeliveryProofAttachments;
import com.deliveryglance.recipientview.RecipientDeliveryFacts;
import com.deliveryglance.recipientview.RecipientViewUpdates;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Delivery use cases a Dispatcher can reach. Each one writes the Delivery and its history in a
 * single transaction, so a Delivery never exists without the transition that explains it.
 *
 * <p>It also answers the two read ports peer modules hold — dispatch's assignment operations and
 * the Recipient view's facts — because both need the Delivery and its active Assignment read
 * together, and that composition is a service's job rather than a repository's.
 */
@Service
class Deliveries implements DeliveryAssignmentOperations, RecipientDeliveryFacts, DeliveryProvisioning {

	private final DeliveryRepository repository;

	private final CurrentActorProvider currentActorProvider;

	private final ActiveAssignments assignments;

	private final NewDeliveryLinks trackingLinks;

	private final RecipientViewUpdates recipientViews;

	private final DeliveryProofAttachments proofAttachments;

	private final NotificationOutbox notifications;

	private final Clock clock;

	Deliveries(DeliveryRepository repository, CurrentActorProvider currentActorProvider, ActiveAssignments assignments,
			NewDeliveryLinks trackingLinks, RecipientViewUpdates recipientViews,
			DeliveryProofAttachments proofAttachments, NotificationOutbox notifications, Clock clock) {
		this.repository = repository;
		this.currentActorProvider = currentActorProvider;
		this.assignments = assignments;
		this.trackingLinks = trackingLinks;
		this.recipientViews = recipientViews;
		this.proofAttachments = proofAttachments;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional
	DeliveryViews.Detail create(DeliveryRequests.Create request) {
		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		UUID id = UUID.randomUUID();
		Instant now = this.clock.instant();

		try {
			this.repository.insertDelivery(id, request, now);
		}
		catch (DuplicateKeyException ex) {
			throw DeliveryConflictException.referenceTaken(request.reference());
		}
		this.repository.insertTransition(UUID.randomUUID(), id, null, DeliveryState.AWAITING_COURIER, actor, null, null,
				null, now);
		// Same transaction as the Delivery itself: ADR 06 says the link exists and is valid from
		// creation, so a Delivery that committed without one would be permanently untrackable.
		this.trackingLinks.createFor(id, now);

		return requireDetail(id);
	}

	/**
	 * The same creation, reached without a request body. It goes through {@link #create} rather than
	 * alongside it so that a provisioned Delivery is indistinguishable from one a Dispatcher typed:
	 * same validation of shape at the database, same first transition, same Tracking Link, same
	 * transaction.
	 */
	@Override
	@Transactional
	public void createAwaitingCourier(NewDelivery delivery) {
		create(new DeliveryRequests.Create(delivery.reference(), address(delivery.pickup()),
				address(delivery.handoff())));
	}

	private static DeliveryRequests.Address address(NewAddress address) {
		return new DeliveryRequests.Address(address.addressLabel(), address.latitude(), address.longitude());
	}

	@Transactional(readOnly = true)
	List<DeliveryViews.Summary> list() {
		return this.repository.findAllNewestFirst();
	}

	@Transactional(readOnly = true)
	DeliveryViews.Detail detail(UUID id) {
		return requireDetail(id);
	}

	@Transactional
	DeliveryViews.Detail cancel(UUID id, DeliveryRequests.Cancel request) {
		// The row lock is taken before anything is decided, so a retry that arrives while the first
		// attempt is still running waits for it and then sees the transition it wrote, rather than
		// racing past the idempotency check and failing on the version it has just bumped.
		DeliveryRepository.CurrentState current = this.repository.lockCurrentState(id)
			.orElseThrow(() -> new DeliveryNotFoundException(id));

		// A retry of a command that already ran: report the result it produced, unchanged.
		if (alreadyHandled(id, request.commandId(), DeliveryState.CANCELLED)) {
			return requireDetail(id);
		}

		if (current.version() != request.expectedVersion()) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		if (!current.state().canTransitionTo(DeliveryState.CANCELLED)) {
			throw DeliveryConflictException.invalidTransition(current.state(), DeliveryState.CANCELLED);
		}

		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		Instant now = this.clock.instant();
		if (this.repository.markState(id, request.expectedVersion(), DeliveryState.CANCELLED, now) != 1) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		UUID transitionId = UUID.randomUUID();
		this.repository.insertTransition(transitionId, id, current.state(), DeliveryState.CANCELLED, actor,
				request.reason(), request.note(), request.commandId(), now);
		if (current.state().endsAssignmentOnMoveTo(DeliveryState.CANCELLED)) {
			this.assignments.endForDelivery(id, now);
		}
		// Reported from inside the transaction, delivered only if it commits. A retry that took the
		// idempotent path above never reaches here, because it changed nothing to report.
		this.recipientViews.deliveryChanged(id);
		// Same transaction, same rule: the outbox row that will notify the Recipient of this
		// cancellation commits with the transition or not at all. It writes nothing unless someone
		// opted in.
		this.notifications.recordTransition(transitionId, now);

		return requireDetail(id);
	}

	@Transactional(readOnly = true)
	Optional<DeliveryViews.CourierDelivery> currentForCourier() {
		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		return this.assignments.activeForCourier(actor.accountId())
			.flatMap((assignment) -> this.repository.findCourierDelivery(assignment.deliveryId()));
	}

	@Transactional
	void confirmPickup(UUID deliveryId, DeliveryRequests.Progress request) {
		progress(deliveryId, request, DeliveryState.ASSIGNED, DeliveryState.IN_TRANSIT, null);
	}

	@Transactional
	void confirmHandoff(UUID deliveryId, DeliveryRequests.Progress request) {
		progress(deliveryId, request, DeliveryState.IN_TRANSIT, DeliveryState.DELIVERED, proofKeys(request.proof()));
	}

	private static DeliveryProofAttachments.ProofKeys proofKeys(DeliveryRequests.Proof proof) {
		return (proof == null) ? null
				: new DeliveryProofAttachments.ProofKeys(proof.photoObjectKey(), proof.signatureObjectKey());
	}

	private void progress(UUID deliveryId, DeliveryRequests.Progress request, DeliveryState requiredState,
			DeliveryState nextState, DeliveryProofAttachments.ProofKeys proof) {
		DeliveryRepository.CurrentState current = this.repository.lockCurrentState(deliveryId)
			.orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
		if (alreadyHandled(deliveryId, request.commandId(), nextState)) {
			return;
		}

		// Ownership is settled before the version is looked at, so a Courier who holds no
		// Assignment on this Delivery is refused without being told its state or version.
		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		ActiveAssignments.ActiveAssignment assignment = this.assignments.activeForDelivery(deliveryId)
			.orElseThrow(DeliveryConflictException::notAssignedToCourier);
		if (!assignment.courierId().equals(actor.accountId())) {
			throw DeliveryConflictException.notAssignedToCourier();
		}

		if (current.version() != request.expectedVersion()) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		if (current.state() != requiredState || !current.state().canTransitionTo(nextState)) {
			throw DeliveryConflictException.invalidTransition(current.state(), nextState);
		}

		Instant now = this.clock.instant();
		if (this.repository.markState(deliveryId, request.expectedVersion(), nextState, now) != 1) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		UUID transitionId = UUID.randomUUID();
		this.repository.insertTransition(transitionId, deliveryId, current.state(), nextState, actor, null, null,
				request.commandId(), now);
		if (current.state().endsAssignmentOnMoveTo(nextState)) {
			this.assignments.endForDelivery(deliveryId, now);
		}
		// Same transaction as the transition it proves: a captured handoff records its proof
		// references here, so proof and the completion it belongs to are never seen apart. Reached
		// only on the real path — a retried command returned above without changing anything.
		if (proof != null) {
			this.proofAttachments.attachAtHandoff(deliveryId, proof, now);
		}
		this.recipientViews.deliveryChanged(deliveryId);
		this.notifications.recordTransition(transitionId, now);
	}

	/**
	 * Whether this command already ran and its result should simply be reported again. A command
	 * identifier that turns up against a different Delivery, or a different transition, is a reused
	 * identifier rather than a retry.
	 */
	private boolean alreadyHandled(UUID deliveryId, UUID commandId, DeliveryState nextState) {
		Optional<DeliveryRepository.HandledCommand> handled = this.repository.findCommandById(commandId);
		if (handled.isEmpty()) {
			return false;
		}
		if (!handled.get().deliveryId().equals(deliveryId) || handled.get().nextState() != nextState) {
			throw DeliveryConflictException.commandIdReused();
		}
		return true;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<AssignmentTarget> assignmentTarget(UUID deliveryId) {
		return this.repository.findAssignmentTarget(deliveryId);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<AssignmentTarget> lockAssignmentTarget(UUID deliveryId) {
		return this.repository.lockAssignmentTarget(deliveryId);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UUID> deliveryIdForCommand(UUID commandId) {
		return this.repository.findDeliveryIdByCommandId(commandId);
	}

	@Override
	@Transactional
	public void transitionToAssigned(UUID deliveryId, int expectedVersion, UUID commandId, Instant occurredAt) {
		DeliveryRepository.CurrentState current = this.repository.lockCurrentState(deliveryId)
			.orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
		if (current.version() != expectedVersion) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		if (!current.state().canTransitionTo(DeliveryState.ASSIGNED)) {
			throw DeliveryConflictException.invalidTransition(current.state(), DeliveryState.ASSIGNED);
		}

		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		if (this.repository.markState(deliveryId, expectedVersion, DeliveryState.ASSIGNED, occurredAt) != 1) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		UUID transitionId = UUID.randomUUID();
		this.repository.insertTransition(transitionId, deliveryId, current.state(), DeliveryState.ASSIGNED, actor, null,
				null, commandId, occurredAt);
		this.recipientViews.deliveryChanged(deliveryId);
		this.notifications.recordTransition(transitionId, occurredAt);
	}

	/**
	 * The Recipient view's read. The Courier is attached from the active Assignment, so a Delivery
	 * that has left Assigned or In Transit has no Courier to attach and the projection downstream
	 * has none to withhold.
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<RecipientDelivery> recipientFactsFor(UUID deliveryId) {
		return this.repository.findRecipientDelivery(deliveryId)
			.map((delivery) -> this.assignments.activeForDelivery(deliveryId)
				.map((active) -> delivery.withCourier(active.courierId(), active.courierDisplayName()))
				.orElse(delivery));
	}

	private DeliveryViews.Detail requireDetail(UUID id) {
		DeliveryViews.Detail detail = this.repository.findDetail(id).orElseThrow(() -> new DeliveryNotFoundException(id));
		return detail.withAssignment(this.assignments.activeForDelivery(id)
			.map((active) -> new DeliveryViews.Assignment(active.courierId(), active.courierDisplayName(),
					active.assignedAt()))
			.orElse(null));
	}

}
