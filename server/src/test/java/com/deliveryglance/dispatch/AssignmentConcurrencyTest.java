package com.deliveryglance.dispatch;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.TestClockConfiguration;
import com.deliveryglance.TimeControlledIntegrationTest;
import com.jayway.jsonpath.JsonPath;
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

/** Real PostgreSQL races: the partial unique indexes and transaction leave one coherent winner. */
@TimeControlledIntegrationTest
class AssignmentConcurrencyTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void simultaneousAssignmentsGiveOneCourierOnlyOneActiveDelivery() throws Exception {
		TestCourier courier = createEligibleCourier(10.0);
		String firstDelivery = createDelivery(signedInDispatcher(), 10.0);
		String secondDelivery = createDelivery(signedInDispatcher(), 10.0);
		BrowserLikeClient firstDispatcher = signedInDispatcher();
		BrowserLikeClient secondDispatcher = signedInDispatcher();

		List<MockHttpServletResponse> responses = race(
				() -> assign(firstDispatcher, firstDelivery, courier.id()),
				() -> assign(secondDispatcher, secondDelivery, courier.id()));

		assertOneWinner(responses);
		assertThat(this.jdbcClient.sql("""
				SELECT count(*) FROM assignment
				WHERE courier_account_id = :courierId AND ended_at IS NULL
				""").param("courierId", courier.id()).query(Integer.class).single()).isEqualTo(1);
		assertCoherentDeliveryPair(firstDelivery, secondDelivery);
	}

	@Test
	void simultaneousAssignmentsGiveOneDeliveryOnlyOneActiveCourier() throws Exception {
		TestCourier firstCourier = createEligibleCourier(20.0);
		TestCourier secondCourier = createEligibleCourier(20.00001);
		String delivery = createDelivery(signedInDispatcher(), 20.0);
		BrowserLikeClient firstDispatcher = signedInDispatcher();
		BrowserLikeClient secondDispatcher = signedInDispatcher();

		List<MockHttpServletResponse> responses = race(
				() -> assign(firstDispatcher, delivery, firstCourier.id()),
				() -> assign(secondDispatcher, delivery, secondCourier.id()));

		assertOneWinner(responses);
		assertThat(this.jdbcClient.sql("""
				SELECT count(*) FROM assignment WHERE delivery_id = :deliveryId AND ended_at IS NULL
				""").param("deliveryId", UUID.fromString(delivery)).query(Integer.class).single()).isEqualTo(1);
		assertThat(this.jdbcClient.sql("SELECT state || ':' || version FROM delivery WHERE id = :deliveryId")
			.param("deliveryId", UUID.fromString(delivery)).query(String.class).single()).isEqualTo("ASSIGNED:1");
		assertThat(this.jdbcClient.sql("SELECT count(*) FROM delivery_transition WHERE delivery_id = :deliveryId")
			.param("deliveryId", UUID.fromString(delivery)).query(Integer.class).single()).isEqualTo(2);
	}

	private TestCourier createEligibleCourier(double latitude) throws Exception {
		int sequence = SEQUENCE.incrementAndGet();
		UUID id = UUID.randomUUID();
		String email = "race-courier-%d@delivery-glance.example".formatted(sequence);
		this.jdbcClient.sql("""
				INSERT INTO internal_account (id, email, password_hash, display_name, role, enabled)
				SELECT :id, :email, password_hash, :displayName, 'COURIER', true
				FROM internal_account WHERE email = :sourceEmail
				""")
			.param("id", id)
			.param("email", email)
			.param("displayName", "Race Courier " + sequence)
			.param("sourceEmail", DemoAccounts.COURIER_EMAIL)
			.update();

		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(email, DemoAccounts.COURIER_PASSWORD);
		client.send(put("/api/couriers/me/duty").contentType(MediaType.APPLICATION_JSON)
			.content("{\"onDuty\":true}"));
		MockHttpServletResponse started = client.send(post("/api/couriers/me/location-sharing"));
		String generation = JsonPath.read(started.getContentAsString(), "$.generation");
		String secret = JsonPath.read(started.getContentAsString(), "$.reportingSecret");
		MockHttpServletResponse reported = client.send(post("/api/couriers/me/location-reports")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":-0.1278,"latitude":%s,
					 "accuracyMetres":12.0,"recordedAt":"%s"}
					""".formatted(generation, secret, latitude, TestClockConfiguration.START)));
		assertThat(reported.getStatus()).isEqualTo(200);
		return new TestCourier(id);
	}

	private BrowserLikeClient signedInDispatcher() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
		return client;
	}

	private String createDelivery(BrowserLikeClient dispatcher, double pickupLatitude) throws Exception {
		MockHttpServletResponse response = dispatcher.send(post("/api/deliveries")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reference":"DG-RACE-%d",
					 "pickup":{"addressLabel":"Warehouse","latitude":%s,"longitude":-0.1278},
					 "handoff":{"addressLabel":"Handoff","latitude":51.5033,"longitude":-0.1195}}
					""".formatted(SEQUENCE.incrementAndGet(), pickupLatitude)));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private MockHttpServletResponse assign(BrowserLikeClient dispatcher, String deliveryId, UUID courierId)
			throws Exception {
		return dispatcher.send(post("/api/deliveries/{id}/assignment", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"courierId":"%s","expectedVersion":0,"commandId":"%s"}
					""".formatted(courierId, UUID.randomUUID())));
	}

	private List<MockHttpServletResponse> race(ThrowingRequest first, ThrowingRequest second) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<MockHttpServletResponse> firstResult = executor.submit(() -> awaitStart(ready, start, first));
			Future<MockHttpServletResponse> secondResult = executor.submit(() -> awaitStart(ready, start, second));
			ready.await();
			start.countDown();
			return List.of(firstResult.get(), secondResult.get());
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static MockHttpServletResponse awaitStart(CountDownLatch ready, CountDownLatch start,
			ThrowingRequest request) throws Exception {
		ready.countDown();
		start.await();
		return request.send();
	}

	private static void assertOneWinner(List<MockHttpServletResponse> responses) throws Exception {
		assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(204, 409);
		assertThat(responses.stream().filter((response) -> response.getStatus() == 409).findFirst()
			.orElseThrow().getContentAsString()).contains("\"code\"");
	}

	private void assertCoherentDeliveryPair(String firstDelivery, String secondDelivery) {
		List<String> deliveries = this.jdbcClient.sql("""
				SELECT state || ':' || version || ':' ||
				       (SELECT count(*) FROM delivery_transition transition WHERE transition.delivery_id = delivery.id)
				FROM delivery WHERE id IN (:firstId, :secondId) ORDER BY state
				""")
			.param("firstId", UUID.fromString(firstDelivery))
			.param("secondId", UUID.fromString(secondDelivery))
			.query(String.class)
			.list();
		assertThat(deliveries).containsExactly("ASSIGNED:1:2", "AWAITING_COURIER:0:1");
	}

	@FunctionalInterface
	private interface ThrowingRequest {

		MockHttpServletResponse send() throws Exception;

	}

	private record TestCourier(UUID id) {
	}

}
