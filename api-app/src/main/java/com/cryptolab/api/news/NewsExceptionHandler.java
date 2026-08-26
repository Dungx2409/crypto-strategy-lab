package com.cryptolab.api.news;

import com.cryptolab.api.shared.ApiError;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {NewsController.class, CrawlerSourceController.class})
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
}
