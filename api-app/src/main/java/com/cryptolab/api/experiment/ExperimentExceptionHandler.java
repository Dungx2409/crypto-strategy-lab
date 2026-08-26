package com.cryptolab.api.experiment;

import com.cryptolab.api.shared.ApiError;
import com.cryptolab.experiment.application.ExperimentNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
    ExperimentController.class,
    LeaderboardController.class,
    PublicLeaderboardController.class,
    MarketDatasetController.class,
    ManualRunController.class
})
final class ExperimentExceptionHandler {

    private final Clock clock;

    ExperimentExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ExperimentNotFoundException.class)
    ResponseEntity<ApiError> notFound(ExperimentNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "EXPERIMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_EXPERIMENT_REQUEST", exception.getMessage());
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
