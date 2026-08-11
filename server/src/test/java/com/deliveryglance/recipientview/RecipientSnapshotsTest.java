package com.deliveryglance.recipientview;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.deliveryglance.delivery.DeliveryState;
import com.deliveryglance.location.LocationFacts;
import com.deliveryglance.recipientview.RecipientDeliveryFacts.RecipientDelivery;
import com.deliveryglance.trackinglink.UnavailableLinkException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The state matrix, asserted from both sides.
 *
 * <p>Every case below is given the <em>same</em> complete set of facts — a Courier, a live position,
 * a completion time — whatever state it is in. So a field that does not appear in the response is a
 * field this projection removed, not one the test forgot to supply, and the two matrix tests
 * together say exactly which fields each state is allowed to carry and that it carries no others.
 */
class RecipientSnapshotsTest {

	private static final UUID DELIVERY_ID = UUID.randomUUID();

	private static final UUID COURIER_ID = UUID.randomUUID();

	private static final String REFERENCE = "DG-0042";

	private static final String HANDOFF_LABEL = "14 Rue de la Paix, Paris";

	private static final double HANDOFF_LATITUDE = 48.8698;

	private static final double HANDOFF_LONGITUDE = 2.3312;

	private static final String CONTACT = "deliveries@delivery-glance.example";

	private static final Instant RECORDED_AT = Instant.parse("2026-03-01T09:59:40Z");

	private static final Instant COMPLETED_AT = Instant.parse("2026-03-01T10:00:00Z");

	private final FakeDeliveryFacts deliveries = new FakeDeliveryFacts();

	private final FakeLocationFacts locations = new FakeLocationFacts();

	private final RecipientSnapshots snapshots = new RecipientSnapshots(this.deliveries, this.locations,
			new RecipientViewProperties(CONTACT));

	/**
	 * What each state may carry. Any component of the response not named here has to be null, which
	 * is the half of this that a new field cannot quietly pass: adding one to the DTO makes it
	 * present in some state and fails the row that did not expect it.
	 */
	private static List<Arguments> theStateMatrix() {
		return List.of(
				Arguments.of(DeliveryState.AWAITING_COURIER, Set.of("reference", "state", "handoffAddressLabel")),
				Arguments.of(DeliveryState.ASSIGNED,
						Set.of("reference", "state", "handoffAddressLabel", "courierDisplayName")),
				Arguments.of(DeliveryState.IN_TRANSIT,
						Set.of("reference", "state", "handoffAddressLabel", "courierDisplayName", "map")),
				Arguments.of(DeliveryState.DELIVERED,
						Set.of("reference", "state", "handoffAddressLabel", "completedAt")),
				Arguments.of(DeliveryState.CANCELLED, Set.of("state", "completedAt", "deliveryTeamContact")));
	}

	@ParameterizedTest
	@MethodSource("theStateMatrix")
	void carriesExactlyTheFieldsItsStateAllowsAndNoOthers(DeliveryState state, Set<String> allowed) {
		this.deliveries.holds(deliveryIn(state));
		this.locations.holds(livePosition());

		assertThat(presentFieldsOf(this.snapshots.of(DELIVERY_ID))).isEqualTo(allowed);
	}

	@ParameterizedTest
	@MethodSource("theStateMatrix")
	void saysTheSameThingAboutEveryFieldItDoesCarry(DeliveryState state, Set<String> allowed) {
		this.deliveries.holds(deliveryIn(state));
		this.locations.holds(livePosition());

		RecipientViews.Snapshot snapshot = this.snapshots.of(DELIVERY_ID);

		assertThat(snapshot.state()).isEqualTo(state);
		if (allowed.contains("reference")) {
			assertThat(snapshot.reference()).isEqualTo(REFERENCE);
		}
		if (allowed.contains("handoffAddressLabel")) {
			assertThat(snapshot.handoffAddressLabel()).isEqualTo(HANDOFF_LABEL);
		}
		if (allowed.contains("courierDisplayName")) {
			assertThat(snapshot.courierDisplayName()).isEqualTo("Cory C.");
		}
		if (allowed.contains("completedAt")) {
			assertThat(snapshot.completedAt()).isEqualTo(COMPLETED_AT);
		}
		if (allowed.contains("deliveryTeamContact")) {
			assertThat(snapshot.deliveryTeamContact()).isEqualTo(CONTACT);
		}
	}

	/**
	 * The rule that "no location outside In Transit" is not a filter somebody has to remember to
	 * apply: the other four states never ask. If one of them ever did, this fake would fail the
	 * matrix tests above rather than let a filter downstream be the only thing protecting it.
	 */
	@ParameterizedTest
	@MethodSource("theStateMatrix")
	void asksLocationWhereTheCourierIsOnlyWhileInTransit(DeliveryState state, Set<String> allowed) {
		this.deliveries.holds(deliveryIn(state));
		this.locations.holds(livePosition());

		this.snapshots.of(DELIVERY_ID);

		assertThat(this.locations.askedAbout)
			.isEqualTo((state == DeliveryState.IN_TRANSIT) ? List.of(COURIER_ID) : List.of());
	}

	@Test
	void putsTheCourierAndTheHandoffOnTheMapWithTheMeasurementTimeUntouched() {
		this.deliveries.holds(deliveryIn(DeliveryState.IN_TRANSIT));
		this.locations.holds(livePosition());

		RecipientViews.MapView map = this.snapshots.of(DELIVERY_ID).map();

		assertThat(map.handoff()).isEqualTo(new RecipientViews.Place(HANDOFF_LATITUDE, HANDOFF_LONGITUDE));
		assertThat(map.courier())
			.isEqualTo(new RecipientViews.CourierPosition(48.8600, 2.3400, 18.0, RECORDED_AT));
	}

	/**
	 * Stop, a withdrawn browser permission and a reading that aged past the usable limit are one
	 * case here, because location answers all three by holding nothing — and the page that results
	 * has to be a map with a destination on it rather than no map at all.
	 */
	@Test
	void keepsTheHandoffMarkerWhenNoUsableCourierPositionExists() {
		this.deliveries.holds(deliveryIn(DeliveryState.IN_TRANSIT));
		this.locations.holds(null);

		RecipientViews.MapView map = this.snapshots.of(DELIVERY_ID).map();

		assertThat(map.courier()).isNull();
		assertThat(map.handoff()).isEqualTo(new RecipientViews.Place(HANDOFF_LATITUDE, HANDOFF_LONGITUDE));
	}

	/** In Transit with no active Assignment is a broken Delivery, not a reason to fail the page. */
	@Test
	void showsTheHandoffAloneWhenInTransitWithoutAnAssignedCourier() {
		this.deliveries.holds(new RecipientDelivery(REFERENCE, DeliveryState.IN_TRANSIT, HANDOFF_LABEL,
				HANDOFF_LATITUDE, HANDOFF_LONGITUDE, null, null, null));

		RecipientViews.Snapshot snapshot = this.snapshots.of(DELIVERY_ID);

		assertThat(snapshot.courierDisplayName()).isNull();
		assertThat(snapshot.map().courier()).isNull();
		assertThat(this.locations.askedAbout).isEmpty();
	}

	@Test
	void offersNoContactAtAllRatherThanAnEmptyOneWhenNoneIsConfigured() {
		this.deliveries.holds(deliveryIn(DeliveryState.CANCELLED));

		RecipientViews.Snapshot snapshot = new RecipientSnapshots(this.deliveries, this.locations,
				new RecipientViewProperties("   ")).of(DELIVERY_ID);

		assertThat(snapshot.deliveryTeamContact()).isNull();
	}

	/**
	 * Authorization already found this Delivery, so its absence here is a race rather than a
	 * refusal — and the Link Holder still has to get the one answer every tracking failure gives.
	 */
	@Test
	void refusesADeliveryThatDisappearedUnderAnAlreadyValidGrant() {
		this.deliveries.holds(null);

		assertThatExceptionOfType(UnavailableLinkException.class).isThrownBy(() -> this.snapshots.of(DELIVERY_ID));
	}

	private static RecipientDelivery deliveryIn(DeliveryState state) {
		return new RecipientDelivery(REFERENCE, state, HANDOFF_LABEL, HANDOFF_LATITUDE, HANDOFF_LONGITUDE, COURIER_ID,
				"Cory C.", COMPLETED_AT);
	}

	private static LocationFacts.TrackedPosition livePosition() {
		return new LocationFacts.TrackedPosition(48.8600, 2.3400, 18.0, RECORDED_AT);
	}

	/** The names of the response components that came back holding something. */
	private static Set<String> presentFieldsOf(RecipientViews.Snapshot snapshot) {
		Set<String> present = new LinkedHashSet<>();
		for (RecordComponent component : RecipientViews.Snapshot.class.getRecordComponents()) {
			try {
				if (component.getAccessor().invoke(snapshot) != null) {
					present.add(component.getName());
				}
			}
			catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("Could not read " + component.getName(), ex);
			}
		}
		return present;
	}

	private static final class FakeDeliveryFacts implements RecipientDeliveryFacts {

		private RecipientDelivery delivery;

		void holds(RecipientDelivery held) {
			this.delivery = held;
		}

		@Override
		public Optional<RecipientDelivery> recipientFactsFor(UUID deliveryId) {
			assertThat(deliveryId).isEqualTo(DELIVERY_ID);
			return Optional.ofNullable(this.delivery);
		}

	}

	private static final class FakeLocationFacts implements LocationFacts {

		private final List<UUID> askedAbout = new ArrayList<>();

		private TrackedPosition position;

		void holds(TrackedPosition held) {
			this.position = held;
		}

		@Override
		public Optional<TrackedPosition> positionForTracking(UUID courierAccountId) {
			this.askedAbout.add(courierAccountId);
			return Optional.ofNullable(this.position);
		}

		@Override
		public CourierLocationFacts factsFor(UUID courierAccountId) {
			throw new AssertionError("The Recipient projection has no business reading Courier workspace status");
		}

		@Override
		public Optional<DispatchPosition> positionForDispatch(UUID courierAccountId) {
			throw new AssertionError("The dispatch coordinate read must never serve a Recipient response");
		}

	}

}
