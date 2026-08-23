package com.cryptolab.api.account;

import com.cryptolab.account.application.AccountService;
import com.cryptolab.account.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    ResponseEntity<AccountResponse> register(
            @RequestBody AccountCredentialsRequest request, HttpServletRequest servletRequest) {
        Account account = accounts.register(request.username(), request.password());
        startSession(servletRequest, account);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @PostMapping("/login")
    AccountResponse login(
            @RequestBody AccountCredentialsRequest request, HttpServletRequest servletRequest) {
        Account account = accounts.authenticate(request.username(), request.password());
        startSession(servletRequest, account);
        return AccountResponse.from(account);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    AuthenticatedAccount me(HttpServletRequest request) {
        return AuthenticatedAccount.require(request.getSession(false));
    }

    private void startSession(HttpServletRequest request, Account account) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(
                AuthenticatedAccount.SESSION_ATTRIBUTE,
                new AuthenticatedAccount(account.id(), account.username()));
    }
}
