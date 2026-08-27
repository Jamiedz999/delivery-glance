package com.deliveryglance.notification;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.deliveryglance.shared.ApiProblemResponses.problem;

/**
 * The notification API's error contract. Declares only {@link NotificationException}, so it renders
 * this module's stable {@code code}s without competing with any other module's advice.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class NotificationExceptionHandler {

	@ExceptionHandler(NotificationException.class)
	ProblemDetail handle(NotificationException exception) {
		return problem(exception.status(), exception.code(), "Notification request refused", exception.getMessage());
	}

}
