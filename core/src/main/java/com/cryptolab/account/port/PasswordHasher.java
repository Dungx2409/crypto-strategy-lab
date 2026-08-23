package com.cryptolab.account.port;

public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String passwordHash);
}
