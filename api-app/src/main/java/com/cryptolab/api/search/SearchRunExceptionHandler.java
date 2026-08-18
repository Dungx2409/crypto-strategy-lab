package com.cryptolab.api.search;

import com.cryptolab.api.shared.ApiError;
import com.cryptolab.experiment.application.SearchRunNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = SearchRunController.class)
final class SearchRunExceptionHandler {

    private final Clock clock;

    SearchRunExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(SearchRunNotFoundException.class)
    ResponseEntity<ApiError> notFound(SearchRunNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "SEARCH_RUN_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_REQUEST", exception.getMessage());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(
                        Instant.now(clock),
                        status.value(),
                        code,
                        message,
                        UUID.randomUUID().toString()));
    }
}
