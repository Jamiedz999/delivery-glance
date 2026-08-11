package com.deliveryglance.recipientview;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.MutableClock;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The refresh stream as a Recipient's browser actually receives it: a real grant cookie, real
 * commands committing against real SQL, and the bytes that come back down the connection.
 *
 * <p>Two things are being proved here and they pull in opposite directions. One is that a page
 * hears about every change it is entitled to hear about. The other is that it hears about nothing
 * else — not another Delivery's commit, not a report the server threw away, not a command that
 * rolled back, and not a Courier moving while the Recipient is not being shown a map. The second
 * list is the one worth having tests for, because every item on it is a change that really
 * happened, so nothing about the code would look wrong if the hint went out anyway.
 *
 * <p>Absence is asserted with a barrier rather than a sleep: after the thing that must send nothing,
 * the test causes a change that must send something, waits for that, and then counts. If the silent
 * change had spoken, its hint would already be sitting in front of the one being waited for.
 */
@TimeControlledIntegrationTest
class RecipientStreamApiTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final String HINT = "event:" + RecipientStreams.SNAPSHOT_CHANGED;

	private static final Duration PATIENCE = Duration.ofSeconds(5);

	private static final String HANDOFF_LABEL = "2 Handoff Road, Recipient Stream Test";

	private static final double COURIER_LATITUDE = 51.5081;

	private static final double COURIER_LONGITUDE = -0.1290;

	private static final double USABLE_ACCURACY_METRES = 14.0;

	/** Wider than the hundred metres a reading has to be inside to be believed. */
	private static final double UNUSABLE_ACCURACY_METRES = 400.0;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private MutableClock clock;

	@Autowired
	private RecipientStreams streams;

	@Autowired
	private RecipientViewUpdates recipientViews;

	@Autowired
	private TransactionTemplate transactions;

	private BrowserLikeClient dispatcher;

	@BeforeEach
	void signInAsDispatcher() throws Exception {
		this.clock.set(TestClockConfiguration.START);
		this.dispatcher = new BrowserLikeClient(this.mockMvc);
		this.dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
	}

	@Test
	void tellsTheChangedDeliverysPageToRefetchAndTellsItNothingElse() throws Exception {
		String delivery = createDelivery();
		TrackedPage page = openPageFor(delivery);

		cancel(delivery);

		String received = page.awaitHints(1);
		// The whole payload: a version, and not one fact about the Delivery it belongs to.
		assertThat(received).contains("data:{\"version\":1}")
			.doesNotContain(delivery)
			.doesNotContain("CANCELLED")
			.doesNotContain(HANDOFF_LABEL);
		page.close();
	}

	@Test
	void neverTellsOnePagesAboutAnotherDeliverysChange() throws Exception {
		String changed = createDelivery();
		String untouched = createDelivery();
		TrackedPage changedPage = openPageFor(changed);
		TrackedPage untouchedPage = openPageFor(untouched);

		cancel(changed);
		changedPage.awaitHints(1);

		// The barrier: the second page is made to receive its own hint, so "nothing yet" cannot be
		// a slow fan-out that had not arrived when the assertion ran.
		cancel(untouched);
		assertThat(untouchedPage.awaitHints(1)).containsOnlyOnce(HINT);

		changedPage.close();
		untouchedPage.close();
	}

	@Test
	void saysNothingWhenACommandWasRefused() throws Exception {
		String delivery = createDelivery();
		TrackedPage page = openPageFor(delivery);

		// The expected version is wrong, so the Delivery is left exactly as it was.
		assertThat(this.dispatcher.send(post("/api/deliveries/{id}/cancel", delivery)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"expectedVersion":7,"reason":"NO_LONGER_REQUIRED","commandId":"%s"}
					""".formatted(UUID.randomUUID())))
			.getStatus()).isEqualTo(409);

		cancel(delivery);
		assertThat(page.awaitHints(1)).containsOnlyOnce(HINT);
		page.close();
	}

	/**
	 * The rule underneath the test above, exercised where it actually lives.
	 *
	 * <p>A command reports its change from inside the transaction that made it, because that is the
	 * only place it can honestly do so — it cannot know yet whether the commit will succeed. What
	 * this proves is that reporting it there is safe: a transaction that then rolls back leaves no
	 * trace on any stream, so no caller has to remember to withdraw anything.
	 */
	@Test
	void saysNothingWhenTheTransactionThatReportedAChangeRolledBack() throws Exception {
		String delivery = createDelivery();
		TrackedPage page = openPageFor(delivery);
		UUID deliveryId = UUID.fromString(delivery);

		assertThatThrownBy(() -> this.transactions.executeWithoutResult((status) -> {
			this.recipientViews.deliveryChanged(deliveryId);
			throw new IllegalStateException("the command failed after reporting its change");
		})).isInstanceOf(IllegalStateException.class);

		cancel(delivery);
		assertThat(page.awaitHints(1)).containsOnlyOnce(HINT);
		page.close();
	}

	@Test
	void saysNothingWhenAReportedLocationWasNotAccepted() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assign(delivery, courier);
		progress(courier, delivery, "pickup", 1);
		TrackedPage page = openPageFor(delivery);

		// Too imprecise to stand in for a position, so Current Location did not move.
		report(courier, UNUSABLE_ACCURACY_METRES, "REJECTED_LOW_ACCURACY");
		// Older than the reading already held, so it did not move either.
		this.clock.advance(Duration.ofSeconds(-10));
		report(courier, USABLE_ACCURACY_METRES, "REJECTED_NOT_NEWER");
		this.clock.advance(Duration.ofSeconds(20));

		report(courier, USABLE_ACCURACY_METRES, "ACCEPTED");
		assertThat(page.awaitHints(1)).containsOnlyOnce(HINT);
		page.close();
	}

	/**
	 * ADR 05 keeps a Courier heading to a pickup off the Recipient's page entirely. A hint carries
	 * no coordinate, but a hint every ten seconds would still describe a Courier on the move, so the
	 * only state an accepted reading is reported in is the one that actually shows a map.
	 */
	@Test
	void saysNothingAboutACourierStillOnTheirWayToCollectTheDelivery() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assign(delivery, courier);
		TrackedPage page = openPageFor(delivery);

		this.clock.advance(Duration.ofSeconds(10));
		report(courier, USABLE_ACCURACY_METRES, "ACCEPTED");

		progress(courier, delivery, "pickup", 1);
		assertThat(page.awaitHints(1)).containsOnlyOnce(HINT);
		page.close();
	}

	@Test
	void tellsAPageWatchingAnInTransitDeliveryThatTheCourierMoved() throws Exception {
		String delivery = createDelivery();
		Courier courier = onDutyCourierSharingLocation();
		assign(delivery, courier);
		progress(courier, delivery, "pickup", 1);
		TrackedPage page = openPageFor(delivery);

		this.clock.advance(Duration.ofSeconds(10));
		report(courier, USABLE_ACCURACY_METRES, "ACCEPTED");

		assertThat(page.awaitHints(1)).contains(HINT);
		page.close();
	}

	/**
	 * The reconnect contract, which is the reason no event is ever replayed: a page that was not
	 * connected when something changed still has the current answer one snapshot read later.
	 */
	@Test
	void givesAPageThatMissedAChangeTheCurrentTruthWhenItComesBack() throws Exception {
		String delivery = createDelivery();
		TrackedPage page = openPageFor(delivery);
		assertThat(page.snapshot()).contains("AWAITING_COURIER");

		page.close();
		cancel(delivery);

		// The same browser, the same grant cookie, a new connection — and no Last-Event-ID, because
		// there is nothing to catch up on.
		MockHttpServletResponse reopened = page.reopen();
		assertThat(reopened.getStatus()).isEqualTo(200);
		assertThat(page.snapshot()).contains("CANCELLED");
		page.close();
	}

	@Test
	void refusesAStreamToABrowserHoldingNoGrant() throws Exception {
		MockHttpServletResponse response = new BrowserLikeClient(this.mockMvc).send(get("/api/tracking/events"));

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(response.getContentAsString()).contains("tracking-link-unavailable");
	}

	@Test
	void refusesAStreamToAGrantThatHasExpired() throws Exception {
		String delivery = createDelivery();
		TrackedPage page = openPageFor(delivery);
		page.close();

		this.clock.advance(Duration.ofDays(8));

		assertThat(page.reopen().getStatus()).isEqualTo(404);
	}

	/**
	 * A stream that was authorised when it opened does not stay open on that authority. The
	 * heartbeat is where an expiry that arrived while nothing else happened gets noticed, and the
	 * connection ends with no frame explaining why.
	 */
	@Test
	void endsAStreamWhoseGrantStoppedAuthorisingIt() throws Exception {
		String delivery = createDelivery();
		TrackedPage page = openPageFor(delivery);
		assertThat(this.streams.openStreamCount()).isEqualTo(1);

		this.clock.advance(Duration.ofDays(8));
		this.streams.heartbeat();

		assertThat(this.streams.openStreamCount()).isZero();
		assertThat(this.streams.watchedDeliveryCount()).isZero();
		assertThat(page.body()).doesNotContain(HINT);
	}

	@Test
	void leavesNothingBehindWhenAPageGoesAway() throws Exception {
		String delivery = createDelivery();
		TrackedPage first = openPageFor(delivery);
		TrackedPage second = openPageFor(delivery);
		assertThat(this.streams.openStreamCount()).isEqualTo(2);
		assertThat(this.streams.watchedDeliveryCount()).isEqualTo(1);

		first.close();
		assertThat(this.streams.openStreamCount()).isEqualTo(1);
		// Still watched by one page, so the version counter is still needed.
		assertThat(this.streams.watchedDeliveryCount()).isEqualTo(1);

		second.close();
		assertThat(this.streams.openStreamCount()).isZero();
		// The counter goes with the last connection; nothing accumulates per Delivery ever tracked.
		assertThat(this.streams.watchedDeliveryCount()).isZero();
	}

	// --- the Link Holder's browser ------------------------------------------------------------

	/**
	 * One Recipient's browser: the grant cookie it exchanged its link for, the stream it is reading
	 * and the snapshot route it refetches through.
	 */
	private final class TrackedPage {

		private final BrowserLikeClient client;

		private MvcResult stream;

		private TrackedPage(BrowserLikeClient client) {
			this.client = client;
		}

		private MockHttpServletResponse reopen() throws Exception {
			this.stream = this.client.open(get("/api/tracking/events"));
			return this.stream.getResponse();
		}

		private String snapshot() throws Exception {
			MockHttpServletResponse response = this.client.send(get("/api/tracking/snapshot"));
			assertThat(response.getStatus()).isEqualTo(200);
			return response.getContentAsString();
		}

		private String body() throws UnsupportedEncodingException {
			return this.stream.getResponse().getContentAsString();
		}

		/**
		 * Blocks until the stream carries the expected number of hints. The writes happen on the
		 * fan-out threads, so there is no request to wait on and no future to join — only the bytes
		 * arriving, which is exactly what the browser is waiting for too.
		 */
		private String awaitHints(int expected) throws Exception {
			long deadline = System.nanoTime() + PATIENCE.toNanos();
			String received = body();
			while (countHints(received) < expected && System.nanoTime() < deadline) {
				Thread.sleep(20);
				received = body();
			}
			assertThat(countHints(received)).as("hints received on the stream").isEqualTo(expected);
			return received;
		}

		/**
		 * What a Recipient closing the tab looks like from in here: the container finishes the
		 * request it was holding open, and the registry has to notice that on its own rather than
		 * being told by the application.
		 */
		private void close() {
			this.stream.getRequest().getAsyncContext().complete();
		}

	}

	private static int countHints(String received) {
		int found = 0;
		int at = received.indexOf(HINT);
		while (at >= 0) {
			found++;
			at = received.indexOf(HINT, at + HINT.length());
		}
		return found;
	}

	// --- fixtures ------------------------------------------------------------------------------

	private TrackedPage openPageFor(String deliveryId) throws Exception {
		BrowserLikeClient holder = new BrowserLikeClient(this.mockMvc);
		holder.send(get("/track"));
		String url = JsonPath.read(this.dispatcher
			.send(post("/api/deliveries/{id}/tracking-link/copy", deliveryId)).getContentAsString(), "$.url");
		assertThat(holder.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(url.substring(url.indexOf("#t=") + 3))))
			.getStatus()).isEqualTo(204);

		TrackedPage page = new TrackedPage(holder);
		assertThat(page.reopen().getStatus()).isEqualTo(200);
		return page;
	}

	private String createDelivery() throws Exception {
		MockHttpServletResponse response = this.dispatcher
			.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
					{"reference":"DG-SSE-%04d",
					 "pickup":{"addressLabel":"1 Depot Lane, Recipient Stream Test","latitude":51.5074,"longitude":-0.1278},
					 "handoff":{"addressLabel":"%s","latitude":51.5090,"longitude":-0.1300}}
					""".formatted(SEQUENCE.incrementAndGet(), HANDOFF_LABEL)));
		assertThat(response.getStatus()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private void cancel(String deliveryId) throws Exception {
		assertThat(this.dispatcher.send(post("/api/deliveries/{id}/cancel", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"expectedVersion":0,"reason":"NO_LONGER_REQUIRED","commandId":"%s"}
					""".formatted(UUID.randomUUID())))
			.getStatus()).isEqualTo(200);
	}

	private void assign(String deliveryId, Courier courier) throws Exception {
		assertThat(this.dispatcher.send(post("/api/deliveries/{id}/assignment", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"courierId":"%s","expectedVersion":0,"commandId":"%s"}
					""".formatted(courier.accountId(), UUID.randomUUID())))
			.getStatus()).isEqualTo(204);
	}

	private void progress(Courier courier, String deliveryId, String action, int expectedVersion) throws Exception {
		assertThat(courier.client().send(post("/api/couriers/me/deliveries/{id}/{action}", deliveryId, action)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"commandId":"%s","expectedVersion":%d}
					""".formatted(UUID.randomUUID(), expectedVersion)))
			.getStatus()).isEqualTo(204);
	}

	private void report(Courier courier, double accuracyMetres, String expectedOutcome) throws Exception {
		MockHttpServletResponse response = courier.client().send(post("/api/couriers/me/location-reports")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"generation":"%s","reportingSecret":"%s","longitude":%s,"latitude":%s,
					 "accuracyMetres":%s,"recordedAt":"%s"}
					""".formatted(courier.generation(), courier.reportingSecret(), COURIER_LONGITUDE, COURIER_LATITUDE,
					accuracyMetres, this.clock.instant())));
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(JsonPath.read(response.getContentAsString(), "$.outcome").toString()).isEqualTo(expectedOutcome);
	}

	private Courier onDutyCourierSharingLocation() throws Exception {
		int sequence = SEQUENCE.incrementAndGet();
		UUID accountId = UUID.randomUUID();
		String email = "courier-stream-%d@delivery-glance.example".formatted(sequence);
		this.jdbcClient.sql("""
				INSERT INTO internal_account (id, email, password_hash, display_name, role, enabled)
				SELECT :id, :email, password_hash, :displayName, 'COURIER', true
				FROM internal_account WHERE email = :sourceEmail
				""")
			.param("id", accountId)
			.param("email", email)
			.param("displayName", "Recipient Stream Courier %d".formatted(sequence))
			.param("sourceEmail", DemoAccounts.COURIER_EMAIL)
			.update();

		BrowserLikeClient client = new BrowserLikeClient(this.mockMvc);
		client.signIn(email, DemoAccounts.COURIER_PASSWORD);
		assertThat(client
			.send(put("/api/couriers/me/duty").contentType(MediaType.APPLICATION_JSON).content("{\"onDuty\":true}"))
			.getStatus()).isEqualTo(200);
		String started = client.send(post("/api/couriers/me/location-sharing")).getContentAsString();
		Courier courier = new Courier(accountId, client, JsonPath.read(started, "$.generation"),
				JsonPath.read(started, "$.reportingSecret"));
		// A Courier with no usable position is not Eligible, so this is what it takes to be
		// assignable at all rather than anything this class is testing.
		report(courier, USABLE_ACCURACY_METRES, "ACCEPTED");
		return courier;
	}

	private record Courier(UUID accountId, BrowserLikeClient client, String generation, String reportingSecret) {
	}

}
