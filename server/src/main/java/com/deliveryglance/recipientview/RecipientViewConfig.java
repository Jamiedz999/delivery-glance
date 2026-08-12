package com.deliveryglance.recipientview;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registers the module's deployment inputs, and the scheduling its stream heartbeat needs. It
 * builds no beans; the module has none to build.
 *
 * <p>{@code @EnableScheduling} is declared here although {@code LocationConfig} also declares it,
 * because a heartbeat that only runs while the location module happens to be on the classpath is a
 * dependency nobody wrote down. Enabling it twice registers one post-processor.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(RecipientViewProperties.class)
class RecipientViewConfig {

}
