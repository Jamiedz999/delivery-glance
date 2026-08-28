package com.deliveryglance.eta;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The provider contract, driven against a real local HTTP endpoint so success, a rate limit, a
 * server error, a timeout, a malformed body and recovery are exercised the way the network produces
 * them — not simulated at the seam. The single rule under test is that every fault becomes an empty
 * result rather than an exception, and that a Mapbox duration becomes a {@link Duration} with no
 * provider type escaping. The adapter is built through {@link EtaConfig} so the real request factory
 * and its timeout are what run.
 */
class HttpTravelTimeAdapterTest {

	private static final List<GeoPoint> ROUTE = List.of(new GeoPoint(51.5000, -0.1000),
			new GeoPoint(51.5200, -0.1300));

	private HttpServer server;

	private final AtomicReference<Responder> responder = new AtomicReference<>();

	private final AtomicReference<String> lastRequest = new AtomicReference<>();

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", (exchange) -> {
			this.lastRequest.set(exchange.getRequestURI().toString());
			this.responder.get().respond(exchange);
		});
		this.server.start();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void turnsAProviderDurationIntoATravelTime() {
		respondWith(200, "{\"code\":\"Ok\",\"routes\":[{\"duration\":540.5}]}");

		assertThat(adapter().travelTime(ROUTE)).contains(Duration.ofSeconds(541));
	}

	@Test
	void asksForNoRouteGeometryAndOrdersCoordinatesLongitudeFirst() {
		respondWith(200, "{\"code\":\"Ok\",\"routes\":[{\"duration\":60}]}");

		adapter().travelTime(ROUTE);

		// ADR 05 forbids storing or requesting route geometry; the request must opt out of it, and
		// Mapbox coordinates are longitude,latitude.
		assertThat(this.lastRequest.get()).contains("overview=false")
			.contains("-0.1,51.5;-0.13,51.52");
	}

	@Test
	void treatsARateLimitAsNoEstimate() {
		respondWith(429, "{\"message\":\"Rate limit exceeded\"}");

		assertThat(adapter().travelTime(ROUTE)).isEmpty();
	}

	@Test
	void treatsAServerErrorAsNoEstimate() {
		respondWith(500, "upstream exploded");

		assertThat(adapter().travelTime(ROUTE)).isEmpty();
	}

	@Test
	void treatsAnEmptyOrMalformedBodyAsNoEstimate() {
		respondWith(200, "{\"code\":\"NoRoute\",\"routes\":[]}");
		assertThat(adapter().travelTime(ROUTE)).isEmpty();

		respondWith(200, "this is not json");
		assertThat(adapter().travelTime(ROUTE)).isEmpty();
	}

	@Test
	void treatsATimeoutAsNoEstimate() {
		// The handler sleeps well past the adapter's read timeout, so the call is abandoned.
		this.responder.set((exchange) -> {
			sleep(Duration.ofMillis(600));
			writeResponse(exchange, 200, "{\"code\":\"Ok\",\"routes\":[{\"duration\":60}]}");
		});

		assertThat(adapter(Duration.ofMillis(150)).travelTime(ROUTE)).isEmpty();
	}

	@Test
	void recoversOnTheNextCallOnceTheProviderAnswersAgain() {
		TravelTimePort adapter = adapter();

		respondWith(503, "unavailable");
		assertThat(adapter.travelTime(ROUTE)).isEmpty();

		respondWith(200, "{\"code\":\"Ok\",\"routes\":[{\"duration\":300}]}");
		assertThat(adapter.travelTime(ROUTE)).contains(Duration.ofSeconds(300));
	}

	private void respondWith(int status, String body) {
		this.responder.set((exchange) -> writeResponse(exchange, status, body));
	}

	private TravelTimePort adapter() {
		return adapter(Duration.ofSeconds(2));
	}

	private TravelTimePort adapter(Duration timeout) {
		TravelTimeProperties properties = new TravelTimeProperties("http://127.0.0.1:" + this.server.getAddress().getPort(),
				"test-token", timeout, 0);
		return new EtaConfig().travelTimePort(properties, Clock.systemUTC());
	}

	private static void writeResponse(HttpExchange exchange, int status, String body) {
		try {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface Responder {

		void respond(HttpExchange exchange) throws IOException;

	}

}
