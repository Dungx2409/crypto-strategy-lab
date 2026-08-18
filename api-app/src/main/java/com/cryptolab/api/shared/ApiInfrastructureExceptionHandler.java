package com.cryptolab.api.shared;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiInfrastructureExceptionHandler {

    private final Clock clock;

    public ApiInfrastructureExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseUnavailable(DataAccessException failure) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                Instant.now(clock),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "DATABASE_UNAVAILABLE",
                "Database is unavailable; the operation was not committed",
                UUID.randomUUID().toString()));
    }
}
