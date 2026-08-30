package com.deliveryglance.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The demo box: the switch on and a schedule configured, which is the only deployment that heals
 * itself.
 *
 * <p>The cron is a daily one in the small hours, so nothing here waits on a timer. What the schedule
 * is for in this test is the wiring — with it set the self-heal bean exists, and the pass it makes
 * when the application is ready is what the first test reads. The recurring pass is the same method,
 * so the second test calls it the way the scheduler would rather than sleeping until it fires.
 *
 * <p>Like {@link DemoResetTest} it runs in its own Spring context and its own PostgreSQL container,
 * because a class that empties every table would otherwise be the reason other tests fail.
 *
 * <p>The order is fixed, and it is the startup pass that needs it: the board it reads is the one the
 * application was started with, and a sibling that reset the demo first would satisfy the assertion
 * with its own work and leave the startup seed unproven.
 */
@IntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = { "delivery-glance.demo.reset-enabled=true",
		"delivery-glance.demo.reset-schedule=0 0 4 * * *" })
class DemoSelfHealTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DemoSelfHeal selfHeal;

	@Autowired
	private ScheduledTaskHolder scheduledTasks;

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
	@Order(1)
	void showsTheFictionalDeliveriesFromStartupWithoutAnybodyCallingTheReset() throws Exception {
		// Nothing in this test created them, and no reset was posted: a visitor arriving at a freshly
		// deployed demo sees the walkthrough's starting board rather than an empty one.
		assertThat(listedReferences()).containsExactlyInAnyOrder("DEMO-1001", "DEMO-1002");

		// And they were made the way the Dispatcher's own form makes one. A reset with nobody acting
		// could only have written rows that resemble a Delivery; this one is attributed.
		String body = this.dispatcher.send(get("/api/deliveries/{id}", demoDeliveryId("DEMO-1001")))
			.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.state")).isEqualTo("AWAITING_COURIER");
		assertThat((String) JsonPath.read(body, "$.transitions[0].actorDisplayName"))
			.isEqualTo(DemoAccounts.DISPATCHER_DISPLAY_NAME);
	}

	@Test
	@Order(2)
	void putsADrivenDemoBackToTheStateTheWalkthroughStartsFrom() throws Exception {
		String stray = idOf(createDelivery("DG-DRIFTED"));
		setDuty(true);
		reportAPosition(startSharing());
		String token = tokenOf(this.dispatcher.send(post("/api/deliveries/{id}/tracking-link/copy",
				demoDeliveryId("DEMO-1001"))));

		this.selfHeal.restoreTheWalkthroughState();

		assertThat(listedReferences()).containsExactlyInAnyOrder("DEMO-1001", "DEMO-1002");
		assertThat(this.dispatcher.send(get("/api/deliveries/{id}", stray)).getStatus()).isEqualTo(404);
		String me = this.courier.send(get("/api/couriers/me")).getContentAsString();
		assertThat((Boolean) JsonPath.read(me, "$.onDuty")).isFalse();
		assertThat(JsonPath.<Object>read(me, "$.sharing")).isNull();
		assertThat((String) JsonPath.read(me, "$.location.freshness")).isEqualTo("UNAVAILABLE");
		assertThat(exchange(token).getStatus()).isEqualTo(404);
	}

	@Test
	@Order(3)
	void healingTheDemoDoesNotOpenTheResetToAnybodyNewer() throws Exception {
		// The schedule is an internal bean call. The route it shares a reset with keeps every one of
		// its refusals, so a scheduled demo is not a demo anyone can wipe.
		this.selfHeal.restoreTheWalkthroughState();

		assertThat(this.courier.send(post("/api/demo/reset")).getStatus()).isEqualTo(403);
		assertThat(this.dispatcher.sendWithoutCsrfHeader(post("/api/demo/reset")).getStatus()).isEqualTo(403);
	}

	@Test
	@Order(4)
	void keepsHealingAfterwards() {
		// The two tests above drive the method the recurring pass calls. This is the pass itself being
		// scheduled: the configured cron is registered against that method, so a demo left alone heals
		// without anybody calling anything. Sitting through a real tick is the only stronger proof,
		// and it is not one a test suite can afford.
		List<ScheduledTask> healing = this.scheduledTasks.getScheduledTasks()
			.stream()
			.filter((task) -> task.toString().endsWith("restoreTheWalkthroughState"))
			.toList();

		assertThat(healing).singleElement()
			.extracting(ScheduledTask::getTask)
			.asInstanceOf(type(CronTask.class))
			.extracting(CronTask::getExpression)
			.isEqualTo("0 0 4 * * *");
	}

	private MockHttpServletResponse createDelivery(String reference) throws Exception {
		return this.dispatcher.send(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content("""
				{"reference":"%s",
				 "pickup":{"addressLabel":"Warehouse 4, Riverside Estate","latitude":51.5074,"longitude":-0.1278},
				 "handoff":{"addressLabel":"Flat 2, 14 Notional Row","latitude":51.5033,"longitude":-0.1195}}
				""".formatted(reference)));
	}

	private List<String> listedReferences() throws Exception {
		return JsonPath.read(this.dispatcher.send(get("/api/deliveries")).getContentAsString(), "$[*].reference");
	}

	private String demoDeliveryId(String reference) throws Exception {
		List<String> ids = JsonPath.read(this.dispatcher.send(get("/api/deliveries")).getContentAsString(),
				"$[?(@.reference == '%s')].id".formatted(reference));
		assertThat(ids).as("the demo should hold %s", reference).hasSize(1);
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

	private MockHttpServletResponse exchange(String token) throws Exception {
		return this.dispatcher.send(post("/api/tracking-session").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(token)));
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
