package com.deliveryglance.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The demo reset, with the switch on.
 *
 * <p>It runs in its own Spring context — and therefore its own PostgreSQL container — for the same
 * reason the reset exists at all: it empties the tables every other integration test writes to, so
 * sharing a database with them would make this class the reason they fail.
 */
@IntegrationTest
@TestPropertySource(properties = "delivery-glance.demo.reset-enabled=true")
class DemoResetTest {

	@Autowired
	private MockMvc mockMvc;

	private BrowserLikeClient dispatcher;

	private BrowserLikeClient courier;

	@BeforeEach
	void signInAsBoth() throws Exception {
		this.dispatcher = new BrowserLikeClient(this.mockMvc);
		this.dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
		this.courier = new BrowserLikeClient(this.mockMvc);
		this.courier.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD);
	}

	@Test
	void replacesEveryDeliveryWithTheFictionalOnesAndSaysWhichItMade() throws Exception {
		String strayId = idOf(createDelivery("DG-LEFT-BEHIND"));

		MockHttpServletResponse response = reset();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<List<String>>read(response.getContentAsString(), "$.createdReferences"))
			.containsExactly("DEMO-1001", "DEMO-1002");
		assertThat(listedReferences()).containsExactlyInAnyOrder("DEMO-1001", "DEMO-1002");
		assertThat(this.dispatcher.send(get("/api/deliveries/{id}", strayId)).getStatus()).isEqualTo(404);
	}

	@Test
	void makesEachFictionalDeliveryTheSameWayTheDispatcherWouldHave() throws Exception {
		reset();
		String id = demoDeliveryId("DEMO-1001");

		String body = this.dispatcher.send(get("/api/deliveries/{id}", id)).getContentAsString();

		assertThat((String) JsonPath.read(body, "$.state")).isEqualTo("AWAITING_COURIER");
		assertThat((Integer) JsonPath.read(body, "$.version")).isZero();
		assertThat(JsonPath.<List<Object>>read(body, "$.transitions")).hasSize(1);
		assertThat((String) JsonPath.read(body, "$.transitions[0].actorDisplayName"))
			.isEqualTo(DemoAccounts.DISPATCHER_DISPLAY_NAME);
		assertThat(JsonPath.<Object>read(body, "$.assignment")).isNull();
		// Made through the Dispatcher's own use case, so the Tracking Link that always arrives with a
		// Delivery arrived with this one too rather than having to be added afterwards.
		assertThat(this.dispatcher.send(post("/api/deliveries/{id}/tracking-link/copy", id)).getStatus())
			.isEqualTo(200);
	}

	@Test
	void endsTheCouriersDutyAndForgetsTheirSharedPosition() throws Exception {
		setDuty(true);
		reportAPosition(startSharing());
		assertThat(freshness()).isEqualTo("LIVE");

		reset();

		String me = this.courier.send(get("/api/couriers/me")).getContentAsString();
		assertThat((Boolean) JsonPath.read(me, "$.onDuty")).isFalse();
		assertThat(JsonPath.<Object>read(me, "$.sharing")).isNull();
		// Coordinates live in memory, so deleting the sharing row alone would not have removed them:
		// a Courier who had stopped sharing would still be on somebody's map for up to two minutes.
		assertThat((String) JsonPath.read(me, "$.location.freshness")).isEqualTo("UNAVAILABLE");
	}

	@Test
	void makesEveryTrackingLinkIssuedBeforeItUnusable() throws Exception {
		reset();
		String token = tokenOf(this.dispatcher.send(post("/api/deliveries/{id}/tracking-link/copy",
				demoDeliveryId("DEMO-1001"))));

		reset();

		MockHttpServletResponse exchange = this.dispatcher.send(post("/api/tracking-session")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(token)));
		assertThat(exchange.getStatus()).isEqualTo(404);
	}

	@Test
	void leavesTheTwoInternalAccountsAlone() throws Exception {
		reset();

		BrowserLikeClient returning = new BrowserLikeClient(this.mockMvc);
		assertThat(returning.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD).getStatus())
			.isEqualTo(204);
		assertThat(returning.signIn(DemoAccounts.COURIER_EMAIL, DemoAccounts.COURIER_PASSWORD).getStatus())
			.isEqualTo(204);
	}

	@Test
	void canBeRunAgainAndProducesTheSameDemoEachTime() throws Exception {
		reset();

		MockHttpServletResponse second = reset();

		assertThat(JsonPath.<List<String>>read(second.getContentAsString(), "$.createdReferences"))
			.containsExactly("DEMO-1001", "DEMO-1002");
		assertThat(listedReferences()).containsExactlyInAnyOrder("DEMO-1001", "DEMO-1002");
	}

	@Test
	void isRefusedForACourier() throws Exception {
		assertThat(this.courier.send(post("/api/demo/reset")).getStatus()).isEqualTo(403);
	}

	@Test
	void isRefusedWithoutTheCsrfHeaderEvenForTheDispatcher() throws Exception {
		assertThat(this.dispatcher.sendWithoutCsrfHeader(post("/api/demo/reset")).getStatus()).isEqualTo(403);
	}

	private MockHttpServletResponse reset() throws Exception {
		return this.dispatcher.send(post("/api/demo/reset"));
	}

	private MockHttpServletResponse createDelivery(String reference) throws Exception {
		return this.dispatcher.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
				{"reference":"%s",
				 "pickup":{"addressLabel":"Warehouse 4, Riverside Estate","latitude":51.5074,"longitude":-0.1278},
				 "handoff":{"addressLabel":"Flat 2, 14 Elm Row","latitude":51.5033,"longitude":-0.1195}}
				""".formatted(reference)));
	}

	private List<String> listedReferences() throws Exception {
		return JsonPath.read(this.dispatcher.send(get("/api/deliveries")).getContentAsString(), "$[*].reference");
	}

	private String demoDeliveryId(String reference) throws Exception {
		List<String> ids = JsonPath.read(this.dispatcher.send(get("/api/deliveries")).getContentAsString(),
				"$[?(@.reference == '%s')].id".formatted(reference));
		assertThat(ids).as("the reset should have created %s", reference).hasSize(1);
		return ids.getFirst();
	}

	private void setDuty(boolean onDuty) throws Exception {
		this.courier.send(put("/api/couriers/me/duty").contentType(MediaType.APPLICATION_JSON)
			.content("{\"onDuty\":%b}".formatted(onDuty)));
	}

	private SharingSession startSharing() throws Exception {
		String body = this.courier.send(post("/api/couriers/me/location-sharing")).getContentAsString();
		return new SharingSession(UUID.fromString(JsonPath.read(body, "$.generation")),
				JsonPath.read(body, "$.reportingSecret"));
	}

	private void reportAPosition(SharingSession session) throws Exception {
		MockHttpServletResponse response = this.courier
			.send(post("/api/couriers/me/location-reports").contentType(MediaType.APPLICATION_JSON).content("""
					{"generation":"%s","reportingSecret":"%s","latitude":51.5005,"longitude":-0.1205,
					 "accuracyMetres":12.0,"recordedAt":"%s"}
					""".formatted(session.generation(), session.reportingSecret(), Instant.now())));
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.outcome")).isEqualTo("ACCEPTED");
	}

	private String freshness() throws Exception {
		return JsonPath.read(this.courier.send(get("/api/couriers/me")).getContentAsString(), "$.location.freshness");
	}

	private static String idOf(MockHttpServletResponse response) throws Exception {
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private static String tokenOf(MockHttpServletResponse response) throws Exception {
		String url = JsonPath.read(response.getContentAsString(), "$.url");
		return url.substring(url.indexOf("#t=") + "#t=".length());
	}

	private record SharingSession(UUID generation, String reportingSecret) {
	}

}
