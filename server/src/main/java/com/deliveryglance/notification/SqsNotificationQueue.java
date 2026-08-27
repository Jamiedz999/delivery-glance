package com.deliveryglance.notification;

import java.util.UUID;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Sends a transition id to the SQS queue named in {@link NotificationProperties}. The body is the
 * bare id as text: the consumer resolves everything else — channel, target, the state-derived
 * message — through the begin callback, so the message that sits in the queue names a transition and
 * discloses nothing about the Recipient.
 */
class SqsNotificationQueue implements NotificationQueue {

	private final SqsClient sqs;

	private final NotificationProperties properties;

	SqsNotificationQueue(SqsClient sqs, NotificationProperties properties) {
		this.sqs = sqs;
		this.properties = properties;
	}

	@Override
	public void enqueue(UUID transitionId) {
		this.sqs.sendMessage(SendMessageRequest.builder()
			.queueUrl(this.properties.queueUrl())
			.messageBody(transitionId.toString())
			.build());
	}

}
