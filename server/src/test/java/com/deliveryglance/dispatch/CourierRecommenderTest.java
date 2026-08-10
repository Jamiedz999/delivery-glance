package com.deliveryglance.dispatch;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourierRecommenderTest {

	private static final Instant CALCULATED_AT = Instant.parse("2026-08-10T09:00:00Z");

	private static final CourierRecommender.Point PICKUP = new CourierRecommender.Point(51.5074, -0.1278);

	@Test
	void ordersEligibleCouriersByHaversineDistanceToPickup() {
		CourierRecommender.CourierSnapshot farther = courier("00000000-0000-0000-0000-000000000002",
				"Farther Courier", 51.5200, -0.1278);
		CourierRecommender.CourierSnapshot nearer = courier("00000000-0000-0000-0000-000000000001",
				"Nearer Courier", 51.5080, -0.1278);

		CourierRecommender.Recommendation recommendation = CourierRecommender.recommend(PICKUP,
				List.of(farther, nearer), CALCULATED_AT);

		assertThat(recommendation.calculatedAt()).isEqualTo(CALCULATED_AT);
		assertThat(recommendation.candidates()).extracting(CourierRecommender.Candidate::courierId)
			.containsExactly(nearer.courierId(), farther.courierId());
		assertThat(recommendation.candidates().getFirst().distanceMetres())
			.isLessThan(recommendation.candidates().getLast().distanceMetres());
	}

	@Test
	void returnsAllEligibleCouriersWhenThereAreFewerThanThreeAndCapsTheListAtThree() {
		List<CourierRecommender.CourierSnapshot> twoCouriers = List.of(
				courier("00000000-0000-0000-0000-000000000001", "One", 51.5080, -0.1278),
				courier("00000000-0000-0000-0000-000000000002", "Two", 51.5090, -0.1278));

		assertThat(CourierRecommender.recommend(PICKUP, twoCouriers, CALCULATED_AT).candidates()).hasSize(2);

		List<CourierRecommender.CourierSnapshot> fourCouriers = List.of(twoCouriers.get(0), twoCouriers.get(1),
				courier("00000000-0000-0000-0000-000000000003", "Three", 51.5100, -0.1278),
				courier("00000000-0000-0000-0000-000000000004", "Four", 51.5110, -0.1278));
		assertThat(CourierRecommender.recommend(PICKUP, fourCouriers, CALCULATED_AT).candidates()).hasSize(3);
	}

	@Test
	void breaksEqualDistanceTiesWithTheStableCourierId() {
		CourierRecommender.CourierSnapshot higherId = courier("00000000-0000-0000-0000-000000000002", "Higher",
				51.5074, -0.1278);
		CourierRecommender.CourierSnapshot lowerId = courier("00000000-0000-0000-0000-000000000001", "Lower",
				51.5074, -0.1278);

		assertThat(CourierRecommender.recommend(PICKUP, List.of(higherId, lowerId), CALCULATED_AT).candidates())
			.extracting(CourierRecommender.Candidate::courierId)
			.containsExactly(lowerId.courierId(), higherId.courierId());
	}

	@Test
	void excludesOffDutyBusyStaleAndLocationlessCouriers() {
		CourierRecommender.CourierSnapshot eligible = courier("00000000-0000-0000-0000-000000000001", "Eligible",
				51.5080, -0.1278);
		CourierRecommender.CourierSnapshot offDuty = new CourierRecommender.CourierSnapshot(
				UUID.fromString("00000000-0000-0000-0000-000000000002"), "Off duty", false, false,
				eligible.position());
		CourierRecommender.CourierSnapshot busy = new CourierRecommender.CourierSnapshot(
				UUID.fromString("00000000-0000-0000-0000-000000000003"), "Busy", true, true, eligible.position());
		CourierRecommender.CourierSnapshot stale = new CourierRecommender.CourierSnapshot(
				UUID.fromString("00000000-0000-0000-0000-000000000004"), "Stale", true, false,
				new CourierRecommender.Position(51.5080, -0.1278, CALCULATED_AT.minusSeconds(121)));
		CourierRecommender.CourierSnapshot locationless = new CourierRecommender.CourierSnapshot(
				UUID.fromString("00000000-0000-0000-0000-000000000005"), "Locationless", true, false, null);

		assertThat(CourierRecommender
			.recommend(PICKUP, List.of(offDuty, busy, stale, locationless, eligible), CALCULATED_AT).candidates())
			.extracting(CourierRecommender.Candidate::displayName)
			.containsExactly("Eligible");
	}

	@Test
	void appliesNoServiceZoneFilterInCore() {
		CourierRecommender.CourierSnapshot courier = courier("00000000-0000-0000-0000-000000000001", "Any area",
				52.0, -1.0);

		assertThat(CourierRecommender.recommend(PICKUP, List.of(courier), CALCULATED_AT).candidates())
			.extracting(CourierRecommender.Candidate::courierId)
			.containsExactly(courier.courierId());
	}

	private static CourierRecommender.CourierSnapshot courier(String id, String displayName, double latitude,
			double longitude) {
		return new CourierRecommender.CourierSnapshot(UUID.fromString(id), displayName, true, false,
				new CourierRecommender.Position(latitude, longitude, CALCULATED_AT));
	}

}
