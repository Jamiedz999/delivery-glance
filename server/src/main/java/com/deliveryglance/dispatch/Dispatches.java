package com.deliveryglance.dispatch;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.deliveryglance.courier.CourierAvailability;
import com.deliveryglance.delivery.DeliveryAssignmentOperations;
import com.deliveryglance.delivery.DeliveryState;
import com.deliveryglance.location.LocationFacts;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class Dispatches {

	private final DeliveryAssignmentOperations deliveries;

	private final CourierAvailability couriers;

	private final LocationFacts locations;

	private final AssignmentRepository assignments;

	private final Clock clock;

	Dispatches(DeliveryAssignmentOperations deliveries, CourierAvailability couriers, LocationFacts locations,
			AssignmentRepository assignments, Clock clock) {
		this.deliveries = deliveries;
		this.couriers = couriers;
		this.locations = locations;
		this.assignments = assignments;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	CourierRecommender.Recommendation recommend(UUID deliveryId) {
		DeliveryAssignmentOperations.AssignmentTarget target = this.deliveries.assignmentTarget(deliveryId)
			.orElseThrow(DispatchException::deliveryNotFound);
		if (target.state() != DeliveryState.AWAITING_COURIER) {
			throw DispatchException.deliveryChanged();
		}

		Instant calculatedAt = this.clock.instant();
		Set<UUID> busyCourierIds = this.assignments.activeCourierIds();
		List<CourierRecommender.CourierSnapshot> snapshots = this.couriers.allCouriers()
			.stream()
			.map((courier) -> snapshot(courier, busyCourierIds.contains(courier.courierId())))
			.toList();
		return CourierRecommender.recommend(
				new CourierRecommender.Point(target.pickupLatitude(), target.pickupLongitude()), snapshots, calculatedAt);
	}

	@Transactional
	void assign(UUID deliveryId, DispatchRequests.Assign request) {
		// Every Direct Assignment takes durable locks in this order: Delivery, then Courier. Two
		// competing commands therefore wait rather than each building half an Assignment.
		DeliveryAssignmentOperations.AssignmentTarget target = this.deliveries.lockAssignmentTarget(deliveryId)
			.orElseThrow(DispatchException::deliveryNotFound);

		Optional<UUID> assignmentCommand = this.assignments.deliveryIdForCommand(request.commandId());
		if (assignmentCommand.isPresent()) {
			if (!assignmentCommand.get().equals(deliveryId)) {
				throw DispatchException.commandIdReused();
			}
			return;
		}
		if (this.deliveries.deliveryIdForCommand(request.commandId()).isPresent()) {
			throw DispatchException.commandIdReused();
		}
		if (target.version() != request.expectedVersion() || target.state() != DeliveryState.AWAITING_COURIER) {
			throw DispatchException.deliveryChanged();
		}

		CourierAvailability.Courier courier = this.couriers.lockCourier(request.courierId())
			.orElseThrow(DispatchException::courierNotEligible);
		Instant assignedAt = this.clock.instant();
		CourierRecommender.CourierSnapshot snapshot = snapshot(courier,
				this.assignments.activeForCourier(courier.courierId()).isPresent());
		if (!CourierRecommender.eligible(snapshot, assignedAt)) {
			throw DispatchException.courierNotEligible();
		}
		try {
			this.assignments.insert(deliveryId, courier.courierId(), request.commandId(), assignedAt);
			this.deliveries.transitionToAssigned(deliveryId, request.expectedVersion(), request.commandId(), assignedAt);
		}
		catch (DataIntegrityViolationException exception) {
			throw DispatchException.assignmentConflict();
		}
	}

	private CourierRecommender.CourierSnapshot snapshot(CourierAvailability.Courier courier, boolean busy) {
		CourierRecommender.Position position = this.locations.positionForDispatch(courier.courierId())
			.map((location) -> new CourierRecommender.Position(location.latitude(), location.longitude(),
					location.recordedAt()))
			.orElse(null);
		return new CourierRecommender.CourierSnapshot(courier.courierId(), courier.displayName(), courier.onDuty(), busy,
				position);
	}

}
