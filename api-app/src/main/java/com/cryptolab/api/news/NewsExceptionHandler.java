package com.cryptolab.api.news;

import com.cryptolab.api.shared.ApiError;
import com.cryptolab.api.account.UnauthenticatedException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {NewsController.class, CrawlerTemplateController.class})
final class NewsExceptionHandler {

    private final Clock clock;

    NewsExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidRequest(IllegalArgumentException failure) {
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(clock),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_NEWS_REQUEST",
                failure.getMessage(),
                UUID.randomUUID().toString()));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ApiError> unauthorized(UnauthenticatedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(
                Instant.now(clock), HttpStatus.UNAUTHORIZED.value(),
                "AUTHENTICATION_REQUIRED", failure.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> unavailable(IllegalStateException failure) {
        String code = failure.getMessage() != null && failure.getMessage().contains("GEMINI_API_KEY")
                ? "GEMINI_NOT_CONFIGURED" : "NEWS_SERVICE_UNAVAILABLE";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                Instant.now(clock), HttpStatus.SERVICE_UNAVAILABLE.value(),
                code, failure.getMessage(), UUID.randomUUID().toString()));
    }
}
