package com.deliveryglance;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Forwards direct navigation/refresh on client-side routes to the React shell, so browser-history
 * routing works without a matching server-side handler for every path.
 */
@Configuration
class SpaWebConfig implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
		registry.addViewController("/**/{path:[^\\.]*}").setViewName("forward:/index.html");
	}

}
