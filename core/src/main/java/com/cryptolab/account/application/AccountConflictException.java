package com.cryptolab.account.application;

public final class AccountConflictException extends RuntimeException {

    public AccountConflictException(String message) {
        super(message);
    }
}
