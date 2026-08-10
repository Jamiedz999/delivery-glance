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
class Deliveries {

	private final DeliveryRepository repository;

	private final CurrentActorProvider currentActorProvider;

	private final Clock clock;

	Deliveries(DeliveryRepository repository, CurrentActorProvider currentActorProvider, Clock clock) {
		this.repository = repository;
		this.currentActorProvider = currentActorProvider;
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
		this.repository.insertTransition(id, null, DeliveryState.AWAITING_COURIER, actor.accountId(), null, null, null,
				now);

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
		Optional<UUID> alreadyHandled = this.repository.findDeliveryIdByCommandId(request.commandId());
		if (alreadyHandled.isPresent()) {
			if (!alreadyHandled.get().equals(id)) {
				throw DeliveryConflictException.commandIdReused();
			}
			// A retry of a command that already ran: report the result it produced, unchanged.
			return requireDetail(id);
		}

		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		DeliveryRepository.Head head = this.repository.lockHead(id).orElseThrow(() -> new DeliveryNotFoundException(id));
		if (head.version() != request.expectedVersion()) {
			throw DeliveryConflictException.versionConflict(head.state(), head.version());
		}
		if (head.state() != DeliveryState.AWAITING_COURIER) {
			throw DeliveryConflictException.invalidTransition(head.state(), DeliveryState.CANCELLED);
		}

		Instant now = this.clock.instant();
		if (this.repository.markCancelled(id, request.expectedVersion(), now) != 1) {
			throw DeliveryConflictException.versionConflict(head.state(), head.version());
		}
		this.repository.insertTransition(id, head.state(), DeliveryState.CANCELLED, actor.accountId(), request.reason(),
				request.note(), request.commandId(), now);

		return requireDetail(id);
	}

	private DeliveryViews.Detail requireDetail(UUID id) {
		return this.repository.findDetail(id).orElseThrow(() -> new DeliveryNotFoundException(id));
	}

}
