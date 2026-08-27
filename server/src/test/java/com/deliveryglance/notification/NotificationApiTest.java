package com.deliveryglance.notification;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The off-band notification pipeline against a real PostgreSQL, driven through the HTTP API and the
 * relay. It asserts the epic's floor: a notify-worthy transition writes exactly one outbox row in
 * the same transaction and only for a Recipient who opted in; the consumer's begin/sent handshake
 * sends exactly once and never twice on redelivery; an unsubscribe suppresses even a queued message;
 * and a callback without the shared token is refused.
 *
 * <p>The broker is a recording double rather than LocalStack: what this class proves is the
 * application's own logic — the outbox, the idempotency and the suppression — for which a real SQS
 * would add a container without adding a claim under test. The queue seam's send is covered
 * separately.
 */
@IntegrationTest
@Import(NotificationApiTest.RecordingQueueConfig.class)
class NotificationApiTest {

	private static final String CALLBACK_TOKEN = "test-notify-token";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@DynamicPropertySource
	static void notificationProperties(DynamicPropertyRegistry registry) {
		// A configured deployment, so opt-in is available and the relay is live — but pointed at a
		// recording double, and with the scheduler set an hour out so only manual relay passes run.
		registry.add("delivery-glance.notification.queue-url", () -> "http://sqs.local/000000000000/notify-test");
		registry.add("delivery-glance.notification.callback-token", () -> CALLBACK_TOKEN);
		registry.add("delivery-glance.notification.relay-interval", () -> "PT1H");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private NotificationRelay relay;

	@Autowired
	private RecordingQueue queue;

	private BrowserLikeClient dispatcher;

	@BeforeEach
	void signInAsDispatcher() throws Exception {
		this.dispatcher = new BrowserLikeClient(this.mockMvc);
		this.dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
	}

	@Test
	void writesExactlyOneOutboxRowInTheSameTransactionOnlyForAnOptedInRecipient() throws Exception {
		String delivery = createDelivery();
		optIn(delivery, "EMAIL", "recipient@example.com");

		cancel(delivery);

		assertThat(outboxRows(delivery)).isEqualTo(1);
		String reference = referenceOf(delivery);
		var row = this.jdbcClient.sql("""
				SELECT next_state, channel, target, delivery_reference,
					published_at IS NULL AS unpublished, sent_at IS NULL AS unsent, suppressed_at IS NULL AS unsuppressed
				FROM notification_outbox WHERE delivery_id = :id
				""").param("id", UUID.fromString(delivery)).query().singleRow();
		assertThat(row.get("next_state")).isEqualTo("CANCELLED");
		assertThat(row.get("channel")).isEqualTo("EMAIL");
		assertThat(row.get("target")).isEqualTo("recipient@example.com");
		assertThat(row.get("delivery_reference")).isEqualTo(reference);
		assertThat(row.get("unpublished")).isEqualTo(true);
		assertThat(row.get("unsent")).isEqualTo(true);
		assertThat(row.get("unsuppressed")).isEqualTo(true);
	}

	@Test
	void writesNoOutboxRowForATransitionNobodyOptedInFor() throws Exception {
		String delivery = createDelivery();

		cancel(delivery);

		assertThat(outboxRows(delivery)).isZero();
	}

	@Test
	void sendsOnceAndReportsAlreadySentOnRedeliveryAfterTheSendIsRecorded() throws Exception {
		String delivery = createDelivery();
		optIn(delivery, "EMAIL", "recipient@example.com");
		cancel(delivery);
		UUID transitionId = transitionId(delivery);

		String first = begin(CALLBACK_TOKEN, transitionId).getContentAsString();
		assertThat(read(first, "$.status")).isEqualTo("PROCEED");
		assertThat(read(first, "$.channel")).isEqualTo("EMAIL");
		assertThat(read(first, "$.target")).isEqualTo("recipient@example.com");
		assertThat(read(first, "$.nextState")).isEqualTo("CANCELLED");
		assertThat(read(first, "$.deliveryReference")).isEqualTo(referenceOf(delivery));

		// A redelivery before the send is confirmed still proceeds — nothing has been sent yet.
		assertThat(read(begin(CALLBACK_TOKEN, transitionId).getContentAsString(), "$.status")).isEqualTo("PROCEED");

		assertThat(sent(CALLBACK_TOKEN, transitionId).getStatus()).isEqualTo(204);

		// Every redelivery after the send is a no-op, and carries no channel or target back.
		String afterSent = begin(CALLBACK_TOKEN, transitionId).getContentAsString();
		assertThat(read(afterSent, "$.status")).isEqualTo("ALREADY_SENT");
		assertThat((String) read(afterSent, "$.channel")).isNull();
		assertThat((String) read(afterSent, "$.target")).isNull();
	}

	@Test
	void suppressesAMessageTheRecipientUnsubscribedFromBeforeItWasSent() throws Exception {
		String delivery = createDelivery();
		BrowserLikeClient holder = optIn(delivery, "SMS", "+15551234567");
		cancel(delivery);
		UUID transitionId = transitionId(delivery);

		assertThat(holder.send(delete("/api/tracking/notifications")).getStatus()).isEqualTo(204);

		assertThat(read(begin(CALLBACK_TOKEN, transitionId).getContentAsString(), "$.status")).isEqualTo("SUPPRESSED");
		assertThat(this.jdbcClient.sql("SELECT suppressed_at IS NOT NULL FROM notification_outbox WHERE transition_id = :t")
			.param("t", transitionId).query(Boolean.class).single()).isTrue();
		// It never sent, and a redelivery stays suppressed.
		assertThat(read(begin(CALLBACK_TOKEN, transitionId).getContentAsString(), "$.status")).isEqualTo("SUPPRESSED");
	}

	@Test
	void relayPublishesAnUnpublishedRowOnceToTheQueue() throws Exception {
		String delivery = createDelivery();
		optIn(delivery, "EMAIL", "recipient@example.com");
		cancel(delivery);
		UUID transitionId = transitionId(delivery);

		this.relay.publishBatch(this.queue);
		assertThat(this.queue.enqueued).contains(transitionId);
		assertThat(this.jdbcClient.sql("SELECT published_at IS NOT NULL FROM notification_outbox WHERE transition_id = :t")
			.param("t", transitionId).query(Boolean.class).single()).isTrue();

		// A published row is not relayed again, so the id is enqueued exactly once however often the
		// relay runs.
		this.relay.publishBatch(this.queue);
		assertThat(this.queue.enqueued.stream().filter(transitionId::equals).count()).isEqualTo(1);
	}

	@Test
	void refusesACallbackWithoutTheSharedToken() throws Exception {
		MockHttpServletResponse response = begin(null, UUID.randomUUID());

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(read(response.getContentAsString(), "$.code")).isEqualTo("notification-callback-unauthorized");
	}

	@Test
	void refusesATargetThatDoesNotMatchItsChannel() throws Exception {
		String delivery = createDelivery();
		BrowserLikeClient holder = grantHolderFor(delivery);

		assertThat(subscribe(holder, "EMAIL", "not-an-email").getStatus()).isEqualTo(422);
		MockHttpServletResponse badPhone = subscribe(holder, "SMS", "5551234567");
		assertThat(badPhone.getStatus()).isEqualTo(422);
		assertThat(read(badPhone.getContentAsString(), "$.code")).isEqualTo("notification-invalid-target");
	}

	@Test
	void reportsTheCurrentOptInStateToTheGrantThatCreatedIt() throws Exception {
		String delivery = createDelivery();
		BrowserLikeClient holder = optIn(delivery, "EMAIL", "recipient@example.com");

		String state = holder.send(get("/api/tracking/notifications")).getContentAsString();
		assertThat(read(state, "$.available")).isEqualTo(true);
		assertThat(read(state, "$.subscription.channel")).isEqualTo("EMAIL");
		assertThat(read(state, "$.subscription.target")).isEqualTo("recipient@example.com");
		assertThat(read(state, "$.subscription.active")).isEqualTo(true);

		assertThat(holder.send(delete("/api/tracking/notifications")).getStatus()).isEqualTo(204);
		assertThat(read(holder.send(get("/api/tracking/notifications")).getContentAsString(), "$.subscription.active"))
			.isEqualTo(false);
	}

	// --- Helpers ----------------------------------------------------------------------------------

	private String createDelivery() throws Exception {
		MockHttpServletResponse response = this.dispatcher
			.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
					{"reference":"DG-NOTIF-%04d",
					 "pickup":{"addressLabel":"Depot","latitude":51.5074,"longitude":-0.1278},
					 "handoff":{"addressLabel":"Flat 2","latitude":51.5090,"longitude":-0.1300}}
					""".formatted(SEQUENCE.incrementAndGet())));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private String referenceOf(String deliveryId) throws Exception {
		return JsonPath.read(this.dispatcher.send(get("/api/deliveries/{id}", deliveryId)).getContentAsString(),
				"$.reference");
	}

	private void cancel(String deliveryId) throws Exception {
		assertThat(this.dispatcher.send(post("/api/deliveries/{id}/cancel", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"expectedVersion":0,"reason":"NO_LONGER_REQUIRED","commandId":"%s"}
					""".formatted(UUID.randomUUID())))
			.getStatus()).isEqualTo(200);
	}

	/** A Link Holder's browser with a grant for this Delivery, before any opt-in. */
	private BrowserLikeClient grantHolderFor(String deliveryId) throws Exception {
		BrowserLikeClient holder = new BrowserLikeClient(this.mockMvc);
		holder.send(get("/track"));
		String url = JsonPath.read(this.dispatcher
			.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId)).getContentAsString(), "$.url");
		assertThat(holder.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(url.substring(url.indexOf("#t=") + 3)))).getStatus()).isEqualTo(204);
		return holder;
	}

	private BrowserLikeClient optIn(String deliveryId, String channel, String target) throws Exception {
		BrowserLikeClient holder = grantHolderFor(deliveryId);
		assertThat(subscribe(holder, channel, target).getStatus()).isEqualTo(200);
		return holder;
	}

	private MockHttpServletResponse subscribe(BrowserLikeClient holder, String channel, String target)
			throws Exception {
		return holder.send(post("/api/tracking/notifications").contentType(MediaType.APPLICATION_JSON)
			.content("{\"channel\":\"%s\",\"target\":\"%s\"}".formatted(channel, target)));
	}

	private UUID transitionId(String deliveryId) {
		return this.jdbcClient.sql("SELECT transition_id FROM notification_outbox WHERE delivery_id = :id")
			.param("id", UUID.fromString(deliveryId))
			.query(UUID.class)
			.single();
	}

	private int outboxRows(String deliveryId) {
		return this.jdbcClient.sql("SELECT count(*) FROM notification_outbox WHERE delivery_id = :id")
			.param("id", UUID.fromString(deliveryId))
			.query(Integer.class)
			.single();
	}

	private MockHttpServletResponse begin(String token, UUID transitionId) throws Exception {
		return callback("/api/internal/notifications/begin", token,
				"{\"transitionId\":\"%s\"}".formatted(transitionId));
	}

	private MockHttpServletResponse sent(String token, UUID transitionId) throws Exception {
		return callback("/api/internal/notifications/sent", token, "{\"transitionId\":\"%s\"}".formatted(transitionId));
	}

	private MockHttpServletResponse callback(String path, String token, String body) throws Exception {
		var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return this.mockMvc.perform(request).andReturn().getResponse();
	}

	private static Object read(String json, String path) {
		return JsonPath.read(json, path);
	}

	/**
	 * A recording stand-in for the broker. It records what the relay asked it to enqueue without a
	 * network, and is {@code @Primary} so both the manual relay passes in these tests and the
	 * scheduled one reach it rather than the real SQS client.
	 */
	static final class RecordingQueue implements NotificationQueue {

		private final List<UUID> enqueued = new CopyOnWriteArrayList<>();

		@Override
		public void enqueue(UUID transitionId) {
			this.enqueued.add(transitionId);
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingQueueConfig {

		@Bean
		@Primary
		RecordingQueue recordingQueue() {
			return new RecordingQueue();
		}

	}

}
