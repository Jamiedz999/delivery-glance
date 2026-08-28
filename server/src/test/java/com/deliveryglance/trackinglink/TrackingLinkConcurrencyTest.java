package com.deliveryglance.trackinglink;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.TimeControlledIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.RepeatedTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real PostgreSQL races on one Tracking Link. The row lock {@code lockByDelivery} takes and the
 * revocation table's unique {@code link_id} leave one coherent outcome: a revoked link is never
 * copied, and it is revoked exactly once with exactly one audit row. Modelled on
 * {@code AssignmentConcurrencyTest}, which races the same way against the assignment indexes.
 */
@TimeControlledIntegrationTest
class TrackingLinkConcurrencyTest {

	/**
	 * Repeated, because a race run once has not been observed losing — only observed not happening
	 * to lose. Each repeat builds its own Delivery, so these are independent races.
	 */
	private static final int RACES = 4;

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	/**
	 * Copy and Revocation arrive together. Revocation always wins its own outcome — there is one
	 * revocation and one audit row — while Copy either got in first (200) or is refused because the
	 * link is already revoked (409). What must never happen is a 200 Copy of an already-revoked link,
	 * which is why both take the same row lock rather than reading the status independently.
	 */
	@RepeatedTest(RACES)
	void copyAndRevocationLeaveTheLinkRevokedExactlyOnceAndNeverCopiedAfterwards() throws Exception {
		BrowserLikeClient dispatcher = signedInDispatcher();
		String deliveryId = createDelivery(dispatcher);

		BrowserLikeClient copier = signedInDispatcher();
		BrowserLikeClient revoker = signedInDispatcher();
		List<MockHttpServletResponse> responses = race(
				() -> copier.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId)),
				() -> revoke(revoker, deliveryId));

		int copyStatus = responses.get(0).getStatus();
		int revokeStatus = responses.get(1).getStatus();
		assertThat(revokeStatus).isEqualTo(204);
		assertThat(copyStatus).isIn(200, 409);

		assertThat(statusOf(deliveryId)).isEqualTo("revoked");
		assertThat(auditCountFor(deliveryId)).isEqualTo(1);
	}

	/**
	 * Two Revocations arrive together. Exactly one commits the status change and its audit row; the
	 * other is refused as already revoked. The unique {@code link_id} is the backstop behind the row
	 * lock: even if both somehow read an active link, only one audit row can ever exist.
	 */
	@RepeatedTest(RACES)
	void simultaneousRevocationsRecordExactlyOneOutcome() throws Exception {
		BrowserLikeClient dispatcher = signedInDispatcher();
		String deliveryId = createDelivery(dispatcher);

		BrowserLikeClient first = signedInDispatcher();
		BrowserLikeClient second = signedInDispatcher();
		List<MockHttpServletResponse> responses = race(
				() -> revoke(first, deliveryId),
				() -> revoke(second, deliveryId));

		assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(204, 409);
		assertThat(statusOf(deliveryId)).isEqualTo("revoked");
		assertThat(auditCountFor(deliveryId)).isEqualTo(1);
	}

	private String statusOf(String deliveryId) {
		return this.jdbcClient.sql("SELECT status FROM tracking_link WHERE delivery_id = :id")
			.param("id", UUID.fromString(deliveryId))
			.query(String.class)
			.single();
	}

	private int auditCountFor(String deliveryId) {
		return this.jdbcClient.sql("""
				SELECT count(*) FROM tracking_link_revocation r
				JOIN tracking_link l ON l.link_id = r.link_id WHERE l.delivery_id = :id
				""").param("id", UUID.fromString(deliveryId)).query(Integer.class).single();
	}

	private MockHttpServletResponse revoke(BrowserLikeClient dispatcher, String deliveryId) throws Exception {
		return dispatcher.send(post("/api/deliveries/{id}/tracking-link/revoke", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"SUSPECTED_EXPOSURE\"}"));
	}

	private BrowserLikeClient signedInDispatcher() throws Exception {
		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
		return client;
	}

	private String createDelivery(BrowserLikeClient dispatcher) throws Exception {
		MockHttpServletResponse response = dispatcher.send(post("/api/deliveries")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"reference":"DG-TRKRACE-%04d",
					 "pickup":{"addressLabel":"1 Pickup Street","latitude":51.5074,"longitude":-0.1278},
					 "handoff":{"addressLabel":"2 Handoff Road","latitude":51.5090,"longitude":-0.1300}}
					""".formatted(SEQUENCE.incrementAndGet())));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
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

	@FunctionalInterface
	private interface ThrowingRequest {

		MockHttpServletResponse send() throws Exception;

	}

}
