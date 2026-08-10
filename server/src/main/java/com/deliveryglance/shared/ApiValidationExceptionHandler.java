package com.deliveryglance.shared;

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
 * A rejected request body looks the same whichever module received it, so the answer is written
 * once here rather than copied into every module's own error contract. Module advice stays for the
 * refusals that carry module meaning, such as a Delivery version conflict.
 */
// Ahead of Boot's own problem-details advice, which would otherwise answer without the stable
// `code` this application's clients branch on, but behind the module advice above it.
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RestControllerAdvice
class ApiValidationExceptionHandler {

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
