package com.deliveryglance.notification;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registers the notification module's deployment inputs, the SQS client the relay uses, and the
 * queue seam over it.
 *
 * <p>The client and the queue bean exist only when a {@code queue-url} is configured. A deployment
 * without one has no relay collaborator at all, which is deliberate: the relay is gated on the same
 * property, so an unconfigured deployment neither publishes nor holds a broker client it would never
 * call. Constructing the client makes no network call — a queue url is supplied per request — so its
 * presence proves configuration, not connectivity.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
@EnableScheduling
class NotificationConfig {

	@Bean
	@ConditionalOnProperty("delivery-glance.notification.queue-url")
	SqsClient notificationSqsClient(NotificationProperties properties) {
		SqsClientBuilder builder = SqsClient.builder().region(Region.of(properties.region()));
		if (properties.hasStaticCredentials()) {
			builder.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())));
		}
		if (properties.hasEndpointOverride()) {
			builder.endpointOverride(URI.create(properties.endpointOverride()));
		}
		return builder.build();
	}

	@Bean
	@ConditionalOnProperty("delivery-glance.notification.queue-url")
	NotificationQueue notificationQueue(SqsClient notificationSqsClient, NotificationProperties properties) {
		return new SqsNotificationQueue(notificationSqsClient, properties);
	}

}
