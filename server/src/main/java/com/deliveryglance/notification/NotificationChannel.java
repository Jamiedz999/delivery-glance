package com.deliveryglance.notification;

/**
 * The two channels a Recipient may volunteer. A subscription is one of these and a target that fits
 * it; the choice decides which provider the consumer Lambda sends through — SES for {@link #EMAIL},
 * SNS for {@link #SMS} — and how the target is validated when it is offered.
 */
enum NotificationChannel {

	EMAIL,

	SMS

}
