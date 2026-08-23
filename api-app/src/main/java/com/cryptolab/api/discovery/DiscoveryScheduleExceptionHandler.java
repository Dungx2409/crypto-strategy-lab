package com.cryptolab.api.discovery;

import com.cryptolab.api.account.UnauthenticatedException;
import com.cryptolab.api.shared.ApiError;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DiscoveryScheduleController.class)
final class DiscoveryScheduleExceptionHandler {

    private final Clock clock;

    DiscoveryScheduleExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ApiError> unauthorized(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(RuntimeException exception) {
        HttpStatus status = exception.getMessage() != null && exception.getMessage().contains("was not found")
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return error(status, "INVALID_DISCOVERY_SCHEDULE", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, "DISCOVERY_SCHEDULE_STATE_CONFLICT", exception.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(clock), status.value(), code, message, UUID.randomUUID().toString()));
    }
}
