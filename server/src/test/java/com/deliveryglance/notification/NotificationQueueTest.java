package com.deliveryglance.notification;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The queue seam against a real SQS (a LocalStack container). The idempotency and outbox logic are
 * proven elsewhere with a recording double; what this adds is the one thing a double cannot — that
 * the client the module builds from its properties actually reaches SQS and puts the bare transition
 * id on the queue, with nothing about the Recipient beside it.
 */
@Testcontainers
class NotificationQueueTest {

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:3.8")).withServices("sqs");

	private static SqsClient sqs;

	private static String queueUrl;

	@BeforeAll
	static void createQueue() {
		sqs = SqsClient.builder()
			.endpointOverride(localstack.getEndpoint())
			.region(Region.of(localstack.getRegion()))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.build();
		CreateQueueResponse created = sqs.createQueue(builder -> builder.queueName("notify-queue-test"));
		queueUrl = created.queueUrl();
	}

	@AfterAll
	static void closeClient() {
		sqs.close();
	}

	@Test
	void putsTheBareTransitionIdOnTheQueue() {
		NotificationProperties properties = new NotificationProperties(queueUrl, localstack.getRegion(),
				localstack.getEndpoint().toString(), localstack.getAccessKey(), localstack.getSecretKey(), 50, "token");
		SqsClient client = SqsClient.builder()
			.endpointOverride(URI.create(properties.endpointOverride()))
			.region(Region.of(properties.region()))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())))
			.build();
		SqsNotificationQueue queue = new SqsNotificationQueue(client, properties);
		UUID transitionId = UUID.randomUUID();

		queue.enqueue(transitionId);

		Message message = sqs
			.receiveMessage(builder -> builder.queueUrl(queueUrl).maxNumberOfMessages(1).waitTimeSeconds(5))
			.messages()
			.get(0);
		assertThat(message.body()).isEqualTo(transitionId.toString());
		client.close();
	}

}
