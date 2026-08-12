package com.deliveryglance.location;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Location's own scheduling: forgetting expired coordinates.
 *
 * <p>The annotation is repeated on {@code RecipientViewConfig} rather than hoisted somewhere both
 * modules share. Enabling it twice registers one post-processor and changes nothing at runtime, and
 * what it buys is that neither module holds its timer by accident of another module's
 * {@code @Configuration} being on the classpath — deleting either one leaves the other working.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class LocationConfig {

}
