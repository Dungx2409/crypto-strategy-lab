package com.cryptolab.api.account;

import com.cryptolab.account.application.AccountConflictException;
import com.cryptolab.account.application.AuthenticationFailedException;
import com.cryptolab.api.shared.ApiError;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AccountController.class)
public final class AccountApiExceptionHandler {

    private final Clock clock;

    public AccountApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(AccountConflictException.class)
    ResponseEntity<ApiError> conflict(AccountConflictException exception) {
        return error(HttpStatus.CONFLICT, "ACCOUNT_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({AuthenticationFailedException.class, UnauthenticatedException.class})
    ResponseEntity<ApiError> unauthorized(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT", exception.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(clock), status.value(), code, message, UUID.randomUUID().toString()));
    }
}
