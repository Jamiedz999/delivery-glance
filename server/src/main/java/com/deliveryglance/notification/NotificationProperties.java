package com.deliveryglance.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment inputs for off-band notification. The application's only broker collaborator is the
 * SQS queue the relay publishes to; the consumer Lambda and its SES/SNS providers are configured
 * where they run, not here. Every value that makes the queue reachable — and the shared secret the
 * Lambda presents on the way back — lives in this record.
 *
 * <p>A deployment that sets no {@code queue-url} has no relay: transitions still write outbox rows
 * when a Recipient has opted in, but nothing publishes them and nothing sends. That is the Core
 * baseline running unchanged, and it is why {@link #isConfigured()} gates the relay rather than an
 * exception being thrown at startup.
 *
 * @param queueUrl the SQS queue the relay sends committed transition ids to. Blank disables the
 * relay entirely.
 * @param region the queue's region.
 * @param endpointOverride an SQS endpoint to use instead of the real AWS one. Blank in production;
 * a LocalStack address for the Compose demo and tests, which is the only way the loop runs without
 * an AWS account.
 * @param accessKeyId static credentials for the SQS client. Blank in production, where the default
 * provider chain (an instance role) is used; set for LocalStack, which accepts any value.
 * @param secretAccessKey the secret half of {@code accessKeyId}.
 * @param relayBatchSize the most outbox rows one relay pass publishes. Bounds a single pass so a
 * backlog drains over several ticks rather than one long transaction.
 * @param callbackToken the shared secret the consumer Lambda presents to the begin and sent
 * callbacks. Blank disables both callback routes, so a deployment without the Lambda cannot have a
 * send recorded by anything but the Lambda it never configured.
 */
@ConfigurationProperties("delivery-glance.notification")
record NotificationProperties(String queueUrl, String region, String endpointOverride, String accessKeyId,
		String secretAccessKey, int relayBatchSize, String callbackToken) {

	NotificationProperties {
		region = (region == null || region.isBlank()) ? "us-east-1" : region;
		relayBatchSize = (relayBatchSize <= 0) ? 50 : relayBatchSize;
	}

	boolean isConfigured() {
		return this.queueUrl != null && !this.queueUrl.isBlank();
	}

	boolean hasStaticCredentials() {
		return this.accessKeyId != null && !this.accessKeyId.isBlank() && this.secretAccessKey != null
				&& !this.secretAccessKey.isBlank();
	}

	boolean hasEndpointOverride() {
		return this.endpointOverride != null && !this.endpointOverride.isBlank();
	}

}
