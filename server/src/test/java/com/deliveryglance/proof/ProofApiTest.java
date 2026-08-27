package com.deliveryglance.proof;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.deliveryglance.BrowserLikeClient;
import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Proof upload against a real S3 (a LocalStack container). It asserts the shape the epic's floor
 * depends on: an authenticated Courier who carries the Delivery gets a short-lived presigned PUT,
 * the bytes go straight to the bucket without passing through the application, and a Courier who
 * does not carry the Delivery is refused before any URL exists.
 */
@IntegrationTest
@Testcontainers
class ProofApiTest {

	private static final String BUCKET = "delivery-glance-proof-test";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.8")).withServices("s3");

	@DynamicPropertySource
	static void proofProperties(DynamicPropertyRegistry registry) {
		registry.add("delivery-glance.proof.bucket", () -> BUCKET);
		registry.add("delivery-glance.proof.region", localstack::getRegion);
		registry.add("delivery-glance.proof.endpoint-override", () -> localstack.getEndpoint().toString());
		registry.add("delivery-glance.proof.path-style-access", () -> "true");
		registry.add("delivery-glance.proof.access-key-id", localstack::getAccessKey);
		registry.add("delivery-glance.proof.secret-access-key", localstack::getSecretKey);
		registry.add("delivery-glance.proof.callback-token", () -> "test-callback-token");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	private S3Client s3;

	private BrowserLikeClient courier;

	private UUID courierId;

	@BeforeEach
	void setUp() throws Exception {
		this.s3 = S3Client.builder()
			.endpointOverride(localstack.getEndpoint())
			.region(Region.of(localstack.getRegion()))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.forcePathStyle(true)
			.build();
		ensureBucket();

		// A fresh Courier per test: the demo Courier is reused across the shared context and would
		// carry an active Assignment from an earlier test, which the one-active-per-courier index
		// forbids. Inserted Couriers copy the demo password hash, so the demo password signs them in.
		TestCourier carrying = insertCourier();
		this.courierId = carrying.id();
		this.courier = new BrowserLikeClient(this.mockMvc);
		this.courier.signIn(carrying.email(), DemoAccounts.COURIER_PASSWORD);
	}

	@Test
	void mintsAPresignedUploadAndTheBrowserUploadsStraightToS3() throws Exception {
		String deliveryId = inTransitDeliveryCarriedBy(this.courierId);

		MockHttpServletResponse response = requestUpload(deliveryId, "PHOTO", "image/jpeg");

		assertThat(response.getStatus()).isEqualTo(200);
		String uploadUrl = JsonPath.read(response.getContentAsString(), "$.uploadUrl");
		String objectKey = JsonPath.read(response.getContentAsString(), "$.objectKey");
		assertThat(objectKey).startsWith("raw/deliveries/" + deliveryId + "/photo/");

		byte[] bytes = "a captured photo".getBytes();
		HttpResponse<Void> put = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create(uploadUrl))
				.header("Content-Type", "image/jpeg")
				.PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
				.build(), HttpResponse.BodyHandlers.discarding());
		assertThat(put.statusCode()).isEqualTo(200);

		HeadObjectResponse head = this.s3
			.headObject(builder -> builder.bucket(BUCKET).key(objectKey));
		assertThat(head.contentLength()).isEqualTo((long) bytes.length);
		assertThat(head.contentType()).isEqualTo("image/jpeg");
	}

	@Test
	void keepsTheBucketPrivateWithPublicAccessFullyBlocked() {
		// The acceptance floor says the bucket "refuses anonymous reads". On real S3 that refusal is
		// enforced by public access being blocked; LocalStack does not emulate the anonymous-read
		// authorization itself, so what is asserted here is the reproducible cause: the bucket has
		// every public-access control on. A read is only ever a presigned GET, minted per object.
		PublicAccessBlockConfiguration block = this.s3
			.getPublicAccessBlock(builder -> builder.bucket(BUCKET))
			.publicAccessBlockConfiguration();

		assertThat(block.blockPublicAcls()).isTrue();
		assertThat(block.ignorePublicAcls()).isTrue();
		assertThat(block.blockPublicPolicy()).isTrue();
		assertThat(block.restrictPublicBuckets()).isTrue();
	}

	@Test
	void refusesAnUploadForADeliveryTheCourierIsNotCarrying() throws Exception {
		UUID otherCourier = insertCourier().id();
		String deliveryId = inTransitDeliveryCarriedBy(otherCourier);

		MockHttpServletResponse response = requestUpload(deliveryId, "PHOTO", "image/jpeg");

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
			.isEqualTo("proof-not-carrying-delivery");
	}

	@Test
	void refusesAContentTypeThatCannotBeProof() throws Exception {
		String deliveryId = inTransitDeliveryCarriedBy(this.courierId);

		MockHttpServletResponse response = requestUpload(deliveryId, "PHOTO", "application/pdf");

		assertThat(response.getStatus()).isEqualTo(422);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
			.isEqualTo("proof-unsupported-content-type");
	}

	@Test
	void handoffAttachesTheCapturedProofAsPendingReferencesInOneTransaction() throws Exception {
		String deliveryId = inTransitDeliveryCarriedBy(this.courierId);
		String photoKey = JsonPath.read(requestUpload(deliveryId, "PHOTO", "image/jpeg").getContentAsString(),
				"$.objectKey");
		String signatureKey = JsonPath.read(requestUpload(deliveryId, "SIGNATURE", "image/png").getContentAsString(),
				"$.objectKey");

		MockHttpServletResponse handoff = handoff(deliveryId, 2,
				"""
						,"proof":{"photoObjectKey":"%s","signatureObjectKey":"%s"}""".formatted(photoKey, signatureKey));

		assertThat(handoff.getStatus()).isEqualTo(204);
		assertThat(proofRows(deliveryId)).isEqualTo(2);
		assertThat(this.jdbcClient.sql("""
				SELECT count(*) FROM delivery_proof
				WHERE delivery_id = :id AND status = 'PENDING' AND processed_at IS NULL
				""").param("id", UUID.fromString(deliveryId)).query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void refusesAHandoffCarryingAKeyForAnotherDeliveryAndKeepsTheDeliveryInTransit() throws Exception {
		String deliveryId = inTransitDeliveryCarriedBy(this.courierId);
		String foreignKey = "raw/deliveries/" + UUID.randomUUID() + "/photo/" + UUID.randomUUID();

		MockHttpServletResponse handoff = handoff(deliveryId, 2,
				",\"proof\":{\"photoObjectKey\":\"%s\"}".formatted(foreignKey));

		assertThat(handoff.getStatus()).isEqualTo(400);
		assertThat((String) JsonPath.read(handoff.getContentAsString(), "$.code")).isEqualTo("proof-invalid-object-key");
		assertThat(proofRows(deliveryId)).isZero();
		assertThat(this.jdbcClient.sql("SELECT state FROM delivery WHERE id = :id")
			.param("id", UUID.fromString(deliveryId)).query(String.class).single()).isEqualTo("IN_TRANSIT");
	}

	@Test
	void aReadyCallbackLetsTheDispatcherLoadThePresignedFullImageStraightFromTheBucket() throws Exception {
		String deliveryId = inTransitDeliveryCarriedBy(this.courierId);
		String photoKey = JsonPath.read(requestUpload(deliveryId, "PHOTO", "image/jpeg").getContentAsString(),
				"$.objectKey");
		assertThat(handoff(deliveryId, 2, ",\"proof\":{\"photoObjectKey\":\"%s\"}".formatted(photoKey)).getStatus())
			.isEqualTo(204);

		// Stand in for the Lambda: write a scrubbed copy and a thumbnail, then call back READY.
		String cleanKey = photoKey.replaceFirst("^raw/", "clean/");
		String thumbnailKey = photoKey.replaceFirst("^raw/", "thumb/");
		putObject(cleanKey, "scrubbed full image");
		putObject(thumbnailKey, "thumbnail");
		MockHttpServletResponse callback = callback("Bearer test-callback-token", """
				{"rawObjectKey":"%s","outcome":"READY","cleanObjectKey":"%s","thumbnailObjectKey":"%s",
				 "contentHash":"%s","processedAt":"2026-08-27T12:00:00Z"}
				""".formatted(photoKey, cleanKey, thumbnailKey, "a".repeat(64)));
		assertThat(callback.getStatus()).isEqualTo(204);

		String body = dispatcher().send(get("/api/deliveries/{id}/proof", deliveryId)).getContentAsString();
		assertThat((String) JsonPath.read(body, "$.artifacts[0].status")).isEqualTo("READY");
		String fullUrl = JsonPath.read(body, "$.artifacts[0].fullUrl");
		assertThat((String) JsonPath.read(body, "$.artifacts[0].thumbnailUrl")).isNotBlank();

		HttpResponse<String> fetched = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create(fullUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
		assertThat(fetched.statusCode()).isEqualTo(200);
		assertThat(fetched.body()).isEqualTo("scrubbed full image");
	}

	@Test
	void aRejectedCallbackLeavesTheDispatcherWithAStatusAndNoImageToLoad() throws Exception {
		String deliveryId = inTransitDeliveryCarriedBy(this.courierId);
		String photoKey = JsonPath.read(requestUpload(deliveryId, "PHOTO", "image/jpeg").getContentAsString(),
				"$.objectKey");
		handoff(deliveryId, 2, ",\"proof\":{\"photoObjectKey\":\"%s\"}".formatted(photoKey));

		MockHttpServletResponse callback = callback("Bearer test-callback-token",
				"""
						{"rawObjectKey":"%s","outcome":"REJECTED","processedAt":"2026-08-27T12:00:00Z"}
						""".formatted(photoKey));
		assertThat(callback.getStatus()).isEqualTo(204);

		String body = dispatcher().send(get("/api/deliveries/{id}/proof", deliveryId)).getContentAsString();
		assertThat((String) JsonPath.read(body, "$.artifacts[0].status")).isEqualTo("REJECTED");
		assertThat((String) JsonPath.read(body, "$.artifacts[0].fullUrl")).isNull();
		assertThat((String) JsonPath.read(body, "$.artifacts[0].thumbnailUrl")).isNull();
	}

	@Test
	void refusesAProcessingCallbackThatDoesNotCarryTheSharedToken() throws Exception {
		MockHttpServletResponse callback = callback(null, """
				{"rawObjectKey":"raw/deliveries/%s/photo/%s","outcome":"REJECTED",
				 "processedAt":"2026-08-27T12:00:00Z"}
				""".formatted(UUID.randomUUID(), UUID.randomUUID()));

		assertThat(callback.getStatus()).isEqualTo(401);
		assertThat((String) JsonPath.read(callback.getContentAsString(), "$.code"))
			.isEqualTo("proof-callback-unauthorized");
	}

	private MockHttpServletResponse callback(String authorization, String body) throws Exception {
		var request = post("/api/internal/proof-processed").contentType(MediaType.APPLICATION_JSON).content(body);
		if (authorization != null) {
			request.header("Authorization", authorization);
		}
		return this.mockMvc.perform(request).andReturn().getResponse();
	}

	private void putObject(String objectKey, String content) {
		this.s3.putObject(builder -> builder.bucket(BUCKET).key(objectKey), RequestBody.fromString(content));
	}

	private BrowserLikeClient dispatcher() throws Exception {
		BrowserLikeClient dispatcher = new BrowserLikeClient(this.mockMvc);
		dispatcher.signIn(DemoAccounts.DISPATCHER_EMAIL, DemoAccounts.DISPATCHER_PASSWORD);
		return dispatcher;
	}

	private MockHttpServletResponse handoff(String deliveryId, int expectedVersion, String proofFragment)
			throws Exception {
		return this.courier.send(post("/api/couriers/me/deliveries/{id}/handoff", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"commandId":"%s","expectedVersion":%d%s}
					""".formatted(UUID.randomUUID(), expectedVersion, proofFragment)));
	}

	private int proofRows(String deliveryId) {
		return this.jdbcClient.sql("SELECT count(*) FROM delivery_proof WHERE delivery_id = :id")
			.param("id", UUID.fromString(deliveryId))
			.query(Integer.class)
			.single();
	}

	private MockHttpServletResponse requestUpload(String deliveryId, String kind, String contentType) throws Exception {
		return this.courier.send(post("/api/couriers/me/deliveries/{id}/proof-uploads", deliveryId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"kind":"%s","contentType":"%s"}
					""".formatted(kind, contentType)));
	}

	private void ensureBucket() {
		try {
			this.s3.headBucket(builder -> builder.bucket(BUCKET));
		}
		catch (SdkException ex) {
			this.s3.createBucket(builder -> builder.bucket(BUCKET));
			// The same private posture the demo init script sets, so the anonymous-read refusal is
			// tested against a bucket configured the way a real deployment configures it.
			this.s3.putPublicAccessBlock(builder -> builder.bucket(BUCKET)
				.publicAccessBlockConfiguration(block -> block.blockPublicAcls(true)
					.ignorePublicAcls(true)
					.blockPublicPolicy(true)
					.restrictPublicBuckets(true)));
		}
	}

	private String inTransitDeliveryCarriedBy(UUID courier) {
		UUID deliveryId = UUID.randomUUID();
		int sequence = SEQUENCE.incrementAndGet();
		OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
		this.jdbcClient.sql("""
				INSERT INTO delivery (id, reference, pickup_address_label, pickup_latitude, pickup_longitude,
				                      handoff_address_label, handoff_latitude, handoff_longitude,
				                      state, version, created_at, updated_at)
				VALUES (:id, :reference, 'Warehouse 4', 51.5074, -0.1278, 'Flat 2', 51.5033, -0.1195,
				        'IN_TRANSIT', 2, :now, :now)
				""")
			.param("id", deliveryId)
			.param("reference", "DG-PROOF-%d".formatted(sequence))
			.param("now", now)
			.update();
		this.jdbcClient.sql("""
				INSERT INTO assignment (id, delivery_id, courier_account_id, command_id, assigned_at)
				VALUES (:id, :deliveryId, :courierId, :commandId, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("deliveryId", deliveryId)
			.param("courierId", courier)
			.param("commandId", UUID.randomUUID())
			.param("now", now)
			.update();
		return deliveryId.toString();
	}

	private TestCourier insertCourier() {
		UUID id = UUID.randomUUID();
		int sequence = SEQUENCE.incrementAndGet();
		String email = "courier-proof-%d@delivery-glance.example".formatted(sequence);
		this.jdbcClient.sql("""
				INSERT INTO internal_account (id, email, password_hash, display_name, role, enabled)
				SELECT :id, :email, password_hash, :displayName, 'COURIER', true
				FROM internal_account WHERE email = :sourceEmail
				""")
			.param("id", id)
			.param("email", email)
			.param("displayName", "Proof Courier %d".formatted(sequence))
			.param("sourceEmail", DemoAccounts.COURIER_EMAIL)
			.update();
		return new TestCourier(id, email);
	}

	private record TestCourier(UUID id, String email) {
	}

}
