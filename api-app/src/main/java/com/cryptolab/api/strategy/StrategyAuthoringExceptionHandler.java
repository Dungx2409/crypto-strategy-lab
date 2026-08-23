package com.cryptolab.api.strategy;

import com.cryptolab.api.account.UnauthenticatedException;
import com.cryptolab.api.shared.ApiError;
import com.cryptolab.strategy.application.StrategyAuthoringFailedException;
import com.cryptolab.strategy.application.StrategyDraftNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StrategyAuthoringController.class)
final class StrategyAuthoringExceptionHandler {

    private final Clock clock;

    StrategyAuthoringExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ApiError> unauthorized(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(StrategyDraftNotFoundException.class)
    ResponseEntity<ApiError> notFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, "STRATEGY_DRAFT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(StrategyAuthoringFailedException.class)
    ResponseEntity<ApiError> generationFailed(RuntimeException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "STRATEGY_GENERATION_FAILED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_STRATEGY_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> unavailableOrConflict(IllegalStateException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("GEMINI_API_KEY")) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_NOT_CONFIGURED", exception.getMessage());
        }
        return error(HttpStatus.CONFLICT, "STRATEGY_DRAFT_STATE_CONFLICT", exception.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(clock), status.value(), code, message, UUID.randomUUID().toString()));
    }
}
