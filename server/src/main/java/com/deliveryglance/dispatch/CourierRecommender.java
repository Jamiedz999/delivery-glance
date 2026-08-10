package com.deliveryglance.dispatch;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure recommendation policy: eligibility first, then nearest pickup distance and stable ID. */
final class CourierRecommender {

	private static final double EARTH_RADIUS_METRES = 6_371_000.0;

	private CourierRecommender() {
	}

	static Recommendation recommend(Point pickup, List<CourierSnapshot> couriers, Instant calculatedAt) {
		List<Candidate> candidates = couriers.stream()
			.filter(CourierRecommender::eligible)
			.map((courier) -> new Candidate(courier.courierId(), courier.displayName(),
					haversineMetres(pickup, courier.position())))
			.sorted(Comparator.comparingDouble(Candidate::distanceMetres).thenComparing(Candidate::courierId))
			.limit(3)
			.toList();
		return new Recommendation(calculatedAt, candidates);
	}

	/**
	 * A missing position already means an unusable one: the location module withholds a position
	 * whose age has passed the usable limit, so freshness is not re-decided here.
	 */
	static boolean eligible(CourierSnapshot courier) {
		return courier.onDuty() && !courier.hasActiveDelivery() && courier.position() != null;
	}

	private static double haversineMetres(Point first, Point second) {
		double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
		double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
		double firstLatitude = Math.toRadians(first.latitude());
		double secondLatitude = Math.toRadians(second.latitude());
		double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
				+ Math.cos(firstLatitude) * Math.cos(secondLatitude) * Math.pow(Math.sin(longitudeDelta / 2), 2);
		return 2 * EARTH_RADIUS_METRES * Math.asin(Math.sqrt(haversine));
	}

	record Point(double latitude, double longitude) {
	}

	record CourierSnapshot(UUID courierId, String displayName, boolean onDuty, boolean hasActiveDelivery,
			Point position) {
	}

	record Candidate(UUID courierId, String displayName, double distanceMetres) {
	}

	record Recommendation(Instant calculatedAt, List<Candidate> candidates) {
	}

}
