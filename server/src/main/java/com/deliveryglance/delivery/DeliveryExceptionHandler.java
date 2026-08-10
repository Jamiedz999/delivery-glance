package com.deliveryglance.delivery;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.deliveryglance.shared.ApiProblemResponses.problem;

/**
 * The Delivery API's error contract. Each response carries a stable {@code code} the browser can
 * branch on, and a conflict also carries the current facts so the client can re-render instead of
 * guessing.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DeliveryController.class)
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

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
				"The request could not be accepted; see errors for the fields to correct.");
		List<FieldMessage> errors = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((error) -> new FieldMessage(error.getField(), message(error)))
			.toList();
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
		return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
				"The request body could not be read.");
	}

	private static String message(FieldError error) {
		return (error.getDefaultMessage() != null) ? error.getDefaultMessage() : "is invalid";
	}

	record FieldMessage(String field, String message) {
	}

}
