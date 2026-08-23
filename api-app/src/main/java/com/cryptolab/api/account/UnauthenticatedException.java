package com.cryptolab.api.account;

public final class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Login is required");
    }
}
