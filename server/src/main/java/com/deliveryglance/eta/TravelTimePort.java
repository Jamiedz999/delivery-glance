package com.deliveryglance.eta;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The module's one external boundary: driving time along an ordered list of waypoints. It is the
 * whole of what ADR 05 lets an ETA depend on — a duration, never a route. A provider returns how
 * long the legs take and nothing a browser could reconstruct a path from; no geometry, polyline or
 * instruction ever crosses this seam, because the seam has no field to carry one.
 *
 * <p>It is a seam with a real second side. Production is an HTTP adapter over a contract-approved
 * provider; tests drive an in-memory double that answers with fixed durations and fixed failures.
 * Either way a failure is an empty result rather than an exception: a provider that times out, is
 * rate-limited or answers with nonsense is simply "no estimate right now", so a caller cannot let a
 * provider fault escape into a Delivery transaction.
 */
interface TravelTimePort {

	/**
	 * The total driving time across {@code waypoints} in order, or empty when the provider gave no
	 * usable answer. Two waypoints is a single leg; three is the Assigned courier→pickup→handoff
	 * route. An empty result never distinguishes why the provider failed, because the caller treats
	 * every provider fault the same honest way.
	 */
	Optional<Duration> travelTime(List<GeoPoint> waypoints);

}
