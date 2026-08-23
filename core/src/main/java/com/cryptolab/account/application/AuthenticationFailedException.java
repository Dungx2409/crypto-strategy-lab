package com.cryptolab.account.application;

public final class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Username or password is incorrect");
    }
}
