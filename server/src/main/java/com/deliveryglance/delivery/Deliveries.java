package com.deliveryglance.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.identityaccess.CurrentActor;
import com.deliveryglance.identityaccess.CurrentActorProvider;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Delivery use cases a Dispatcher can reach. Each one writes the Delivery and its history in a
 * single transaction, so a Delivery never exists without the transition that explains it.
 */
@Service
class Deliveries implements DeliveryAssignmentOperations {

	private final DeliveryRepository repository;

	private final CurrentActorProvider currentActorProvider;

	private final ActiveAssignments assignments;

	private final Clock clock;

	Deliveries(DeliveryRepository repository, CurrentActorProvider currentActorProvider, ActiveAssignments assignments,
			Clock clock) {
		this.repository = repository;
		this.currentActorProvider = currentActorProvider;
		this.assignments = assignments;
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

		Optional<DeliveryRepository.HandledCommand> alreadyHandled = this.repository.findCommandById(request.commandId());
		if (alreadyHandled.isPresent()) {
			if (!alreadyHandled.get().deliveryId().equals(id)
					|| alreadyHandled.get().nextState() != DeliveryState.CANCELLED) {
				throw DeliveryConflictException.commandIdReused();
			}
			// A retry of a command that already ran: report the result it produced, unchanged.
			return requireDetail(id);
		}

		if (current.version() != request.expectedVersion()) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		if (current.state() != DeliveryState.AWAITING_COURIER && current.state() != DeliveryState.ASSIGNED) {
			throw DeliveryConflictException.invalidTransition(current.state(), DeliveryState.CANCELLED);
		}

		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		Instant now = this.clock.instant();
		if (this.repository.markState(id, request.expectedVersion(), DeliveryState.CANCELLED, now) != 1) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		this.repository.insertTransition(id, current.state(), DeliveryState.CANCELLED, actor, request.reason(),
				request.note(), request.commandId(), now);
		if (current.state() == DeliveryState.ASSIGNED) {
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
		progress(deliveryId, request, DeliveryState.ASSIGNED, DeliveryState.IN_TRANSIT, false);
	}

	@Transactional
	void confirmHandoff(UUID deliveryId, DeliveryRequests.Progress request) {
		progress(deliveryId, request, DeliveryState.IN_TRANSIT, DeliveryState.DELIVERED, true);
	}

	private void progress(UUID deliveryId, DeliveryRequests.Progress request, DeliveryState requiredState,
			DeliveryState nextState, boolean endsAssignment) {
		DeliveryRepository.CurrentState current = this.repository.lockCurrentState(deliveryId)
			.orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
		Optional<DeliveryRepository.HandledCommand> alreadyHandled = this.repository.findCommandById(request.commandId());
		if (alreadyHandled.isPresent()) {
			if (!alreadyHandled.get().deliveryId().equals(deliveryId)
					|| alreadyHandled.get().nextState() != nextState) {
				throw DeliveryConflictException.commandIdReused();
			}
			return;
		}
		if (current.version() != request.expectedVersion()) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}

		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		ActiveAssignments.ActiveAssignment assignment = this.assignments.activeForDelivery(deliveryId)
			.orElseThrow(DeliveryConflictException::notAssignedToCourier);
		if (!assignment.courierId().equals(actor.accountId())) {
			throw DeliveryConflictException.notAssignedToCourier();
		}
		if (current.state() != requiredState) {
			throw DeliveryConflictException.invalidTransition(current.state(), nextState);
		}

		Instant now = this.clock.instant();
		if (this.repository.markState(deliveryId, request.expectedVersion(), nextState, now) != 1) {
			throw DeliveryConflictException.versionConflict(current.state(), current.version());
		}
		this.repository.insertTransition(deliveryId, current.state(), nextState, actor, null, null, request.commandId(),
				now);
		if (endsAssignment) {
			this.assignments.endForDelivery(deliveryId, now);
		}
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
	public void transitionToAssigned(AssignmentTarget target, CurrentActor actor, UUID commandId, Instant occurredAt) {
		if (this.repository.markState(target.deliveryId(), target.version(), DeliveryState.ASSIGNED, occurredAt) != 1) {
			throw DeliveryConflictException.versionConflict(target.state(), target.version());
		}
		this.repository.insertTransition(target.deliveryId(), target.state(), DeliveryState.ASSIGNED, actor, null, null,
				commandId, occurredAt);
	}

	private DeliveryViews.Detail requireDetail(UUID id) {
		DeliveryViews.Detail detail = this.repository.findDetail(id).orElseThrow(() -> new DeliveryNotFoundException(id));
		DeliveryViews.Assignment assignment = this.assignments.activeForDelivery(id)
			.map((active) -> new DeliveryViews.Assignment(active.courierId(), active.courierDisplayName(),
					active.assignedAt()))
			.orElse(null);
		return new DeliveryViews.Detail(detail.id(), detail.reference(), detail.state(), detail.version(), detail.pickup(),
				detail.handoff(), detail.createdAt(), detail.updatedAt(), detail.transitions(), assignment);
	}

}
