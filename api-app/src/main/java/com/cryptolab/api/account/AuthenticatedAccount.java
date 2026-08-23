package com.cryptolab.api.account;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.UUID;

public record AuthenticatedAccount(UUID id, String username) implements Serializable {

    static final String SESSION_ATTRIBUTE = AuthenticatedAccount.class.getName();

    public static AuthenticatedAccount require(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) {
            return account;
        }
        throw new UnauthenticatedException();
    }
}
