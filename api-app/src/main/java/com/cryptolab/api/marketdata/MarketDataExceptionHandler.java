package com.cryptolab.api.marketdata;

import com.cryptolab.api.shared.ApiError;
import com.cryptolab.marketdata.application.InvalidMarketDataRequestException;
import com.cryptolab.marketdata.application.MarketDataUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = MarketDataController.class)
final class MarketDataExceptionHandler {

    private final Clock clock;

    MarketDataExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(InvalidMarketDataRequestException.class)
    ResponseEntity<ApiError> invalidRequest(InvalidMarketDataRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage());
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> malformedRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    ResponseEntity<ApiError> unavailable(MarketDataUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_DATA_UNAVAILABLE", exception.getMessage());
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
