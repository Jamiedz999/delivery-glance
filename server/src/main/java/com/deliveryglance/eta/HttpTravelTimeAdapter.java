package com.deliveryglance.eta;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * The travel-time boundary over a Mapbox Directions endpoint. It asks the provider for a route's
 * total driving time, and returns that duration and nothing else. The provider's own response shape —
 * its geometry, its legs, its status envelope — is a set of records private to this file; they are
 * read to pull one number out and never leave the adapter, so no provider DTO can reach the domain or
 * a Recipient response. That isolation is the ADR 05 rule "provider DTOs never enter the domain" made
 * true by construction rather than by discipline.
 *
 * <p>Every failure is the same answer: empty. A timeout, a 429, a 5xx, a malformed body, a route the
 * provider could not find — all become "no estimate right now", because the caller's honest handling
 * of a missing window is identical for every cause and a provider fault must never surface as an
 * exception a transition could see. The request is explicitly overview-free and duration-annotated,
 * so the provider is never even asked for the geometry ADR 05 forbids storing.
 */
class HttpTravelTimeAdapter implements TravelTimePort {

	private static final Logger logger = LoggerFactory.getLogger(HttpTravelTimeAdapter.class);

	private final RestClient client;

	private final String baseUrl;

	private final String accessToken;

	private final int dailyRequestCap;

	private final Clock clock;

	private final AtomicReference<DailyCount> requests = new AtomicReference<>(new DailyCount(LocalDate.MIN, 0));

	HttpTravelTimeAdapter(RestClient client, TravelTimeProperties properties, Clock clock) {
		this.client = client;
		this.baseUrl = stripTrailingSlash(properties.providerBaseUrl());
		this.accessToken = properties.accessToken();
		this.dailyRequestCap = properties.dailyRequestCap();
		this.clock = clock;
	}

	@Override
	public Optional<Duration> travelTime(List<GeoPoint> waypoints) {
		if (waypoints.size() < 2) {
			return Optional.empty();
		}
		if (!withinDailyCap()) {
			logger.warn("ETA provider daily request cap of {} reached; leaving windows to age out until reset",
					this.dailyRequestCap);
			return Optional.empty();
		}
		try {
			DirectionsResponse response = this.client.get().uri(routeUri(waypoints)).retrieve()
				.body(DirectionsResponse.class);
			return durationFrom(response);
		}
		catch (RuntimeException ex) {
			// Timeout, 4xx (including 429 rate limit), 5xx, or an unreadable body all land here and all
			// mean the same thing to the caller.
			logger.debug("ETA provider call failed; treating as no estimate", ex);
			return Optional.empty();
		}
	}

	private Optional<Duration> durationFrom(DirectionsResponse response) {
		if (response == null || !"Ok".equals(response.code()) || response.routes() == null
				|| response.routes().isEmpty()) {
			return Optional.empty();
		}
		double seconds = response.routes().get(0).duration();
		if (!Double.isFinite(seconds) || seconds < 0) {
			return Optional.empty();
		}
		return Optional.of(Duration.ofSeconds(Math.round(seconds)));
	}

	private URI routeUri(List<GeoPoint> waypoints) {
		StringJoiner coordinates = new StringJoiner(";");
		for (GeoPoint point : waypoints) {
			// Mapbox orders coordinates longitude,latitude. Double.toString is locale-independent, so a
			// decimal point is never rendered as a comma that would split the pair.
			coordinates.add(Double.toString(point.longitude()) + "," + Double.toString(point.latitude()));
		}
		return URI.create(this.baseUrl + "/directions/v5/mapbox/driving/" + coordinates
				+ "?access_token=" + this.accessToken + "&overview=false&annotations=duration");
	}

	private boolean withinDailyCap() {
		if (this.dailyRequestCap <= 0) {
			return true;
		}
		LocalDate today = LocalDate.ofInstant(this.clock.instant(), ZoneOffset.UTC);
		DailyCount current = this.requests
			.updateAndGet((previous) -> previous.day().equals(today) ? previous.increment() : new DailyCount(today, 1));
		return current.count() <= this.dailyRequestCap;
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private record DailyCount(LocalDate day, int count) {

		DailyCount increment() {
			return new DailyCount(this.day, this.count + 1);
		}

	}

	/** The slice of a Mapbox Directions response the adapter reads. Everything else is ignored. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record DirectionsResponse(String code, List<Route> routes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Route(double duration) {
	}

}
