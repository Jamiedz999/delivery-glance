package com.deliveryglance.dispatch;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.TestClockConfiguration;
import com.deliveryglance.TimeControlledIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@TimeControlledIntegrationTest
class DispatchApiTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	private BrowserLikeClient dispatcher;

	@BeforeEach
	void signInAsDispatcher() throws Exception {
		this.dispatcher = new BrowserLikeClient(this.mockMvc);
		this.dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
	}

	@Test
	void recommendsTheNearestEligibleCourierWithoutExposingCoordinates() throws Exception {
		TestCourier farther = createCourier("farther");
		TestCourier nearer = createCourier("nearer");
		makeEligible(farther, 51.50741, -0.1278);
		makeEligible(nearer, 51.5074, -0.1278);
		String deliveryId = createDelivery();

		MockHttpServletResponse response = this.dispatcher
			.send(get("/api/deliveries/{id}/courier-recommendations", deliveryId));

		assertThat(response.getStatus()).isEqualTo(200);
		String body = response.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.calculatedAt")).isEqualTo(TestClockConfiguration.START.toString());
		assertThat(JsonPath.<java.util.List<String>>read(body, "$.candidates[*].courierId"))
			.containsSubsequence(nearer.id().toString(), farther.id().toString());
		assertThat((Number) JsonPath.read(body, "$.candidates[0].distanceMetres")).isNotNull();
		assertThat(body).doesNotContain("latitude").doesNotContain("longitude");
	}

	@Test
	void revalidatesCourierEligibilityWhenTheDispatcherAssigns() throws Exception {
		TestCourier courier = createCourier("changed");
		makeEligible(courier, 51.5080, -0.1278);
		String deliveryId = createDelivery();
		assertThat(this.dispatcher.send(get("/api/deliveries/{id}/courier-recommendations", deliveryId)).getStatus())
			.isEqualTo(200);

		BrowserLikeClient courierClient = new BrowserLikeClient(this.mockMvc);
		courierClient.signIn(courier.email(), DemoAccounts.COURIER_PASSWORD);
		courierClient.send(put("/api/couriers/me/duty").contentType(MediaType.APPLICATION_JSON)
			.content("{\"onDuty\":false}"));

		MockHttpServletResponse response = assign(deliveryId, courier.id(), 0, UUID.randomUUID());

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("courier-not-eligible");
		MockHttpServletResponse detail = this.dispatcher.send(get("/api/deliveries/{id}", deliveryId));
		assertThat((String) JsonPath.read(detail.getContentAsString(), "$.state")).isEqualTo("AWAITING_COURIER");
		assertThat(JsonPath.<java.util.List<Object>>read(detail.getContentAsString(), "$.transitions")).hasSize(1);
		assertThat(this.jdbcClient.sql("SELECT count(*) FROM assignment WHERE delivery_id = :deliveryId")
			.param("deliveryId", UUID.fromString(deliveryId)).query(Integer.class).single()).isZero();
	}

	@Test
	void assignmentRevalidatesEligibilityWithoutRequiringTheCourierToRemainInTheNearestThree() throws Exception {
		TestCourier selected = createCourier("eligible-outside-three");
		makeEligible(selected, 52.0, -0.1278);
		for (int index = 0; index < 3; index++) {
			TestCourier nearer = createCourier("nearer-" + index);
			makeEligible(nearer, 51.5074 + (index * 0.00001), -0.1278);
		}
		String deliveryId = createDelivery();

		MockHttpServletResponse recommendation = this.dispatcher
			.send(get("/api/deliveries/{id}/courier-recommendations", deliveryId));
		assertThat(JsonPath.<java.util.List<String>>read(recommendation.getContentAsString(),
				"$.candidates[*].courierId")).doesNotContain(selected.id().toString());

		assertThat(assign(deliveryId, selected.id(), 0, UUID.randomUUID()).getStatus()).isEqualTo(204);
	}

	@Test
	void directlyAssignsOnceAndTreatsARetryAsTheSameCommand() throws Exception {
		TestCourier courier = createCourier("selected");
		makeEligible(courier, 51.5080, -0.1278);
		String deliveryId = createDelivery();
		UUID commandId = UUID.randomUUID();

		MockHttpServletResponse assigned = assign(deliveryId, courier.id(), 0, commandId);
		MockHttpServletResponse retry = assign(deliveryId, courier.id(), 0, commandId);

		assertThat(assigned.getStatus()).isEqualTo(204);
		assertThat(retry.getStatus()).isEqualTo(204);
		MockHttpServletResponse detail = this.dispatcher.send(get("/api/deliveries/{id}", deliveryId));
		assertThat((String) JsonPath.read(detail.getContentAsString(), "$.state")).isEqualTo("ASSIGNED");
		assertThat((Integer) JsonPath.read(detail.getContentAsString(), "$.version")).isEqualTo(1);
		assertThat((String) JsonPath.read(detail.getContentAsString(), "$.assignment.courierDisplayName"))
			.isEqualTo(courier.displayName());
		assertThat(JsonPath.<java.util.List<Object>>read(detail.getContentAsString(), "$.transitions")).hasSize(2);
		assertThat(this.jdbcClient.sql("SELECT count(*) FROM assignment WHERE delivery_id = :deliveryId AND ended_at IS NULL")
			.param("deliveryId", UUID.fromString(deliveryId)).query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void assignedCourierExplicitlyConfirmsPickupAndHandoff() throws Exception {
		TestCourier courier = createCourier("lifecycle");
		makeEligible(courier, 51.5033, -0.1195);
		String deliveryId = createDelivery();
		assertThat(assign(deliveryId, courier.id(), 0, UUID.randomUUID()).getStatus()).isEqualTo(204);
		BrowserLikeClient courierClient = new BrowserLikeClient(this.mockMvc);
		courierClient.signIn(courier.email(), DemoAccounts.COURIER_PASSWORD);

		MockHttpServletResponse current = courierClient.send(get("/api/couriers/me/deliveries/current"));
		assertThat(current.getStatus()).isEqualTo(200);
		assertThat((String) JsonPath.read(current.getContentAsString(), "$.id")).isEqualTo(deliveryId);
		assertThat((String) JsonPath.read(current.getContentAsString(), "$.state")).isEqualTo("ASSIGNED");
		// Being located at the handoff does not infer completion: the explicit command is still required.
		assertThat(progress(courierClient, deliveryId, "handoff", 1).getStatus()).isEqualTo(409);

		assertThat(progress(courierClient, deliveryId, "pickup", 1).getStatus()).isEqualTo(204);
		MockHttpServletResponse inTransit = this.dispatcher.send(get("/api/deliveries/{id}", deliveryId));
		assertThat((String) JsonPath.read(inTransit.getContentAsString(), "$.state")).isEqualTo("IN_TRANSIT");
		assertThat((Integer) JsonPath.read(inTransit.getContentAsString(), "$.version")).isEqualTo(2);
		assertThat(progress(courierClient, deliveryId, "pickup", 2).getStatus()).isEqualTo(409);

		MockHttpServletResponse cancelAfterPickup = this.dispatcher.send(post("/api/deliveries/{id}/cancel", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"commandId":"%s","expectedVersion":2,"reason":"NO_LONGER_REQUIRED","note":null}
					""".formatted(UUID.randomUUID())));
		assertThat(cancelAfterPickup.getStatus()).isEqualTo(409);

		assertThat(progress(courierClient, deliveryId, "handoff", 2).getStatus()).isEqualTo(204);
		MockHttpServletResponse delivered = this.dispatcher.send(get("/api/deliveries/{id}", deliveryId));
		assertThat((String) JsonPath.read(delivered.getContentAsString(), "$.state")).isEqualTo("DELIVERED");
		assertThat((Integer) JsonPath.read(delivered.getContentAsString(), "$.version")).isEqualTo(3);
		assertThat(JsonPath.<java.util.List<Object>>read(delivered.getContentAsString(), "$.transitions")).hasSize(4);
		assertThat(courierClient.send(get("/api/couriers/me/deliveries/current")).getStatus()).isEqualTo(204);
		assertThat(progress(courierClient, deliveryId, "pickup", 3).getStatus()).isEqualTo(409);
		assertThat(progress(courierClient, deliveryId, "handoff", 3).getStatus()).isEqualTo(409);
		MockHttpServletResponse cancelDelivered = this.dispatcher.send(post("/api/deliveries/{id}/cancel", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"commandId":"%s","expectedVersion":3,"reason":"NO_LONGER_REQUIRED","note":null}
					""".formatted(UUID.randomUUID())));
		assertThat(cancelDelivered.getStatus()).isEqualTo(409);
		assertThat(this.jdbcClient.sql("SELECT count(*) FROM assignment WHERE delivery_id = :deliveryId AND ended_at IS NULL")
			.param("deliveryId", UUID.fromString(deliveryId)).query(Integer.class).single()).isZero();
	}

	@Test
	void dispatcherCanCancelAnAssignedDeliveryBeforePickupAndEndItsAssignment() throws Exception {
		TestCourier courier = createCourier("cancelled");
		makeEligible(courier, 51.5080, -0.1278);
		String deliveryId = createDelivery();
		assign(deliveryId, courier.id(), 0, UUID.randomUUID());

		MockHttpServletResponse cancelled = this.dispatcher.send(post("/api/deliveries/{id}/cancel", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"commandId":"%s","expectedVersion":1,"reason":"NO_LONGER_REQUIRED","note":null}
					""".formatted(UUID.randomUUID())));

		assertThat(cancelled.getStatus()).isEqualTo(200);
		assertThat((String) JsonPath.read(cancelled.getContentAsString(), "$.state")).isEqualTo("CANCELLED");
		assertThat(this.jdbcClient.sql("SELECT count(*) FROM assignment WHERE delivery_id = :deliveryId AND ended_at IS NULL")
			.param("deliveryId", UUID.fromString(deliveryId)).query(Integer.class).single()).isZero();
	}

	@Test
	void anotherCourierCannotProgressTheAssignedDelivery() throws Exception {
		TestCourier assignedCourier = createCourier("owner");
		TestCourier otherCourier = createCourier("other");
		makeEligible(assignedCourier, 51.5080, -0.1278);
		String deliveryId = createDelivery();
		assign(deliveryId, assignedCourier.id(), 0, UUID.randomUUID());
		BrowserLikeClient otherClient = new BrowserLikeClient(this.mockMvc);
		otherClient.signIn(otherCourier.email(), DemoAccounts.COURIER_PASSWORD);

		MockHttpServletResponse response = progress(otherClient, deliveryId, "pickup", 1);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
			.isEqualTo("delivery-not-assigned-to-courier");
	}

	private TestCourier createCourier(String name) {
		int sequence = SEQUENCE.incrementAndGet();
		UUID id = UUID.randomUUID();
		String email = "courier-%s-%d@delivery-glance.example".formatted(name, sequence);
		String displayName = "%s Courier %d".formatted(name, sequence);
		this.jdbcClient.sql("""
				INSERT INTO internal_account (id, email, password_hash, display_name, role, enabled)
				SELECT :id, :email, password_hash, :displayName, 'COURIER', true
				FROM internal_account WHERE email = :sourceEmail
				""")
			.param("id", id)
			.param("email", email)
			.param("displayName", displayName)
			.param("sourceEmail", DemoAccounts.COURIER_EMAIL)
			.update();
		return new TestCourier(id, email, displayName);
	}

	private void makeEligible(TestCourier courier, double latitude, double longitude) throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(courier.email(), DemoAccounts.COURIER_PASSWORD);
		assertThat(client.send(put("/api/couriers/me/duty").contentType(MediaType.APPLICATION_JSON)
			.content("{\"onDuty\":true}")).getStatus()).isEqualTo(200);
		MockHttpServletResponse started = client.send(post("/api/couriers/me/location-sharing"));
		String generation = JsonPath.read(started.getContentAsString(), "$.generation");
		String secret = JsonPath.read(started.getContentAsString(), "$.reportingSecret");
		MockHttpServletResponse reported = client.send(post("/api/couriers/me/location-reports")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":%s,"latitude":%s,
					 "accuracyMetres":12.0,"recordedAt":"%s"}
					""".formatted(generation, secret, longitude, latitude, TestClockConfiguration.START)));
		assertThat(reported.getStatus()).isEqualTo(200);
	}

	private String createDelivery() throws Exception {
		MockHttpServletResponse response = this.dispatcher.send(post("/api/deliveries")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reference":"DG-DISPATCH-%d",
					 "pickup":{"addressLabel":"Warehouse 4","latitude":51.5074,"longitude":-0.1278},
					 "handoff":{"addressLabel":"Flat 2","latitude":51.5033,"longitude":-0.1195}}
					""".formatted(SEQUENCE.incrementAndGet())));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private MockHttpServletResponse assign(String deliveryId, UUID courierId, int expectedVersion, UUID commandId)
			throws Exception {
		return this.dispatcher.send(post("/api/deliveries/{id}/assignment", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"courierId":"%s","expectedVersion":%d,"commandId":"%s"}
					""".formatted(courierId, expectedVersion, commandId)));
	}

	private MockHttpServletResponse progress(BrowserLikeClient courierClient, String deliveryId, String action,
			int expectedVersion) throws Exception {
		return courierClient.send(post("/api/couriers/me/deliveries/{id}/{action}", deliveryId, action)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"commandId":"%s","expectedVersion":%d}
					""".formatted(UUID.randomUUID(), expectedVersion)));
	}

	private record TestCourier(UUID id, String email, String displayName) {
	}

}
