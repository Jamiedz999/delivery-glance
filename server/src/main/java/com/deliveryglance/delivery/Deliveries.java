package com.deliveryglance.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.identityaccess.CurrentActor;
import com.deliveryglance.identityaccess.CurrentActorProvider;
import com.deliveryglance.recipientview.RecipientDeliveryFacts;

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
class Deliveries implements DeliveryAssignmentOperations, RecipientDeliveryFacts {

	private final DeliveryRepository repository;

	private final CurrentActorProvider currentActorProvider;

	private final ActiveAssignments assignments;

	private final NewDeliveryLinks trackingLinks;

	private final Clock clock;

	Deliveries(DeliveryRepository repository, CurrentActorProvider currentActorProvider, ActiveAssignments assignments,
			NewDeliveryLinks trackingLinks, Clock clock) {
		this.repository = repository;
		this.currentActorProvider = currentActorProvider;
		this.assignments = assignments;
		this.trackingLinks = trackingLinks;
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
		this.repository.insertTransition(id, null, DeliveryState.AWAITING_COURIER, actor, null, null, null, now);
		// Same transaction as the Delivery itself: ADR 06 says the link exists and is valid from
		// creation, so a Delivery that committed without one would be permanently untrackable.
		this.trackingLinks.createFor(id, now);

		return requireDetail(id);
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
		this.repository.insertTransition(id, current.state(), DeliveryState.CANCELLED, actor, request.reason(),
				request.note(), request.commandId(), now);
		if (current.state().endsAssignmentOnMoveTo(DeliveryState.CANCELLED)) {
			this.assignments.endForDelivery(id, now);
		}

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
		progress(deliveryId, request, DeliveryState.ASSIGNED, DeliveryState.IN_TRANSIT);
	}

	@Transactional
	void confirmHandoff(UUID deliveryId, DeliveryRequests.Progress request) {
		progress(deliveryId, request, DeliveryState.IN_TRANSIT, DeliveryState.DELIVERED);
	}

	private void progress(UUID deliveryId, DeliveryRequests.Progress request, DeliveryState requiredState,
			DeliveryState nextState) {
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
		this.repository.insertTransition(deliveryId, current.state(), nextState, actor, null, null, request.commandId(),
				now);
		if (current.state().endsAssignmentOnMoveTo(nextState)) {
			this.assignments.endForDelivery(deliveryId, now);
		}
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
		this.repository.insertTransition(deliveryId, current.state(), DeliveryState.ASSIGNED, actor, null, null, commandId,
				occurredAt);
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
