package com.cryptolab.api.account;

import com.cryptolab.account.domain.AccountRole;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record AuthenticatedAccount(UUID id, String username, AccountRole role)
        implements Principal, Serializable {

    static final String SESSION_ATTRIBUTE = AuthenticatedAccount.class.getName();

    public static AuthenticatedAccount require(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) {
            return account;
        }
        throw new UnauthenticatedException();
    }

    @Override
    public String getName() {
        return username;
    }
}
