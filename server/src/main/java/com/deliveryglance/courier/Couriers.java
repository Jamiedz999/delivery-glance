package com.deliveryglance.courier;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deliveryglance.identityaccess.CurrentActor;
import com.deliveryglance.identityaccess.CurrentActorProvider;
import com.deliveryglance.location.LocationFacts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a Courier can do to their own record, which in Core is exactly one thing: declare whether
 * they are On Duty. Location Sharing is a separate decision made in a separate module, and this
 * class only reads its coordinate-free facts to present them together.
 */
@Service
class Couriers implements CourierAvailability {

	private final CourierRepository repository;

	private final CurrentActorProvider currentActorProvider;

	private final LocationFacts locationFacts;

	private final Clock clock;

	Couriers(CourierRepository repository, CurrentActorProvider currentActorProvider, LocationFacts locationFacts,
			Clock clock) {
		this.repository = repository;
		this.currentActorProvider = currentActorProvider;
		this.locationFacts = locationFacts;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	CourierViews.Courier me() {
		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		return view(actor, this.repository.findDuty(actor.accountId()));
	}

	@Transactional
	CourierViews.Courier updateDuty(CourierRequests.Duty request) {
		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		CourierRepository.Duty duty = this.repository.saveDuty(actor.accountId(), request.onDuty(),
				this.clock.instant());
		return view(actor, Optional.of(duty));
	}

	@Override
	@Transactional(readOnly = true)
	public List<CourierAvailability.Courier> allCouriers() {
		return this.repository.findAllCourierAvailability();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CourierAvailability.Courier> lockCourier(UUID courierId) {
		return this.repository.lockCourierAvailability(courierId);
	}

	private CourierViews.Courier view(CurrentActor actor, Optional<CourierRepository.Duty> duty) {
		LocationFacts.CourierLocationFacts facts = this.locationFacts.factsFor(actor.accountId());
		return new CourierViews.Courier(actor.displayName(), duty.map(CourierRepository.Duty::onDuty).orElse(false),
				duty.map(CourierRepository.Duty::changedAt).orElse(null),
				(facts.sharingStartedAt() != null) ? new CourierViews.Sharing(facts.sharingStartedAt()) : null,
				facts.location());
	}

}
