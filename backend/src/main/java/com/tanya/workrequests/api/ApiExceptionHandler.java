package com.tanya.workrequests.api;

import com.tanya.workrequests.api.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns every failure into {@code {status, error, message}}. Only whole-request failures get
 * here; problems with individual work requests are returned as data with a 200.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException exception) {
        // Jackson's own message can quote internals; say what's wrong in our words instead.
        log.debug("Rejected unreadable request body: {}", exception.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(InvalidBatchException.class)
    ResponseEntity<ApiError> invalidBatch(InvalidBatchException exception) {
        log.warn("Rejected batch: {}", exception.getMessage());
        return respond(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> anythingElse(Exception exception) {
        // Spring's own exceptions (404, 405, 415, ...) carry their status and a safe detail.
        if (exception instanceof ErrorResponse frameworkError) {
            HttpStatusCode status = frameworkError.getStatusCode();
            String detail = frameworkError.getBody().getDetail();
            return respond(status, detail != null ? detail : ApiError.of(status, "").error());
        }
        log.error("Unexpected error while handling a request", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ResponseEntity<ApiError> respond(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status, message));
    }
}
