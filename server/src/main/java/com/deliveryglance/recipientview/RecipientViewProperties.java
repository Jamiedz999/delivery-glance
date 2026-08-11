package com.deliveryglance.recipientview;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment inputs for the Recipient view.
 *
 * @param deliveryTeamContact the phone number or email address a Recipient is given when a Delivery
 * was cancelled. ADR 05 makes it a single centrally configured value rather than anything derived
 * from the Delivery, so a cancelled page cannot become a way to reach the Dispatcher who cancelled
 * it. Blank means the page says the Delivery Team can be contacted through the channel that shared
 * the link, which is honest and needs no configuration.
 */
@ConfigurationProperties("delivery-glance.recipient")
record RecipientViewProperties(String deliveryTeamContact) {
}
