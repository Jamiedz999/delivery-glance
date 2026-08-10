package com.deliveryglance.delivery;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.deliveryglance.shared.ApiProblemResponses.problem;

/**
 * The Delivery API's error contract. Each response carries a stable {@code code} the browser can
 * branch on, and a conflict also carries the current facts so the client can re-render instead of
 * guessing. A rejected request body is answered by the shared validation advice, which says the
 * same thing for every module.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = { DeliveryController.class, CourierDeliveryController.class })
class DeliveryExceptionHandler {

	@ExceptionHandler(DeliveryNotFoundException.class)
	ProblemDetail handleNotFound(DeliveryNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "delivery-not-found", "Delivery not found",
				"No Delivery matches that identifier.");
	}

	@ExceptionHandler(DeliveryConflictException.class)
	ProblemDetail handleConflict(DeliveryConflictException exception) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, exception.code(), "Delivery command refused",
				exception.getMessage());
		exception.currentFacts().forEach(problem::setProperty);
		return problem;
	}

}
