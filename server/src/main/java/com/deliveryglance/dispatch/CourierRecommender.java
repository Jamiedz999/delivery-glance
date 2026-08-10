package com.deliveryglance.dispatch;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure recommendation policy: eligibility first, then nearest pickup distance and stable ID. */
final class CourierRecommender {

	private static final double EARTH_RADIUS_METRES = 6_371_000.0;

	private static final Duration LOCATION_USABLE_LIMIT = Duration.ofMinutes(2);

	private CourierRecommender() {
	}

	static Recommendation recommend(Point pickup, List<CourierSnapshot> couriers, Instant calculatedAt) {
		List<Candidate> candidates = couriers.stream()
			.filter((courier) -> eligible(courier, calculatedAt))
			.map((courier) -> new Candidate(courier.courierId(), courier.displayName(),
					haversineMetres(pickup, courier.position().point())))
			.sorted(Comparator.comparingDouble(Candidate::distanceMetres).thenComparing(Candidate::courierId))
			.limit(3)
			.toList();
		return new Recommendation(calculatedAt, candidates);
	}

	static boolean eligible(CourierSnapshot courier, Instant calculatedAt) {
		return courier.onDuty() && !courier.busy() && courier.position() != null
				&& Duration.between(courier.position().recordedAt(), calculatedAt).compareTo(LOCATION_USABLE_LIMIT) <= 0;
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

	record Position(double latitude, double longitude, Instant recordedAt) {

		Point point() {
			return new Point(this.latitude, this.longitude);
		}

	}

	record CourierSnapshot(UUID courierId, String displayName, boolean onDuty, boolean busy, Position position) {
	}

	record Candidate(UUID courierId, String displayName, double distanceMetres) {
	}

	record Recommendation(Instant calculatedAt, List<Candidate> candidates) {
	}

}
