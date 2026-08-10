package com.deliveryglance.location;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling exists in this application for one reason: forgetting expired coordinates.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class LocationConfig {

}
