package com.cryptolab.api.account;

import com.cryptolab.account.application.AccountService;
import com.cryptolab.account.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
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
            @RequestBody AccountCredentialsRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Account account = accounts.register(request.username(), request.password());
        startSession(servletRequest, servletResponse, account);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @PostMapping("/login")
    AccountResponse login(
            @RequestBody AccountCredentialsRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Account account = accounts.authenticate(request.username(), request.password());
        startSession(servletRequest, servletResponse, account);
        return AccountResponse.from(account);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    AuthenticatedAccount me(HttpServletRequest request) {
        return AuthenticatedAccount.require(request.getSession(false));
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    private void startSession(
            HttpServletRequest request, HttpServletResponse response, Account account) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        AuthenticatedAccount principal =
                new AuthenticatedAccount(account.id(), account.username(), account.role());
        session.setAttribute(AuthenticatedAccount.SESSION_ATTRIBUTE, principal);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }

    record CsrfResponse(String headerName, String token) {}
}
