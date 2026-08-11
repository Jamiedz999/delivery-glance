package com.deliveryglance.recipientview;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the module's deployment inputs. It builds no beans; the module has none to build. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecipientViewProperties.class)
class RecipientViewConfig {

}
