package com.cryptolab.api.strategy;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.strategy.application.StrategyAuthoringService;
import com.cryptolab.strategy.application.UserStrategyService;
import com.cryptolab.strategy.domain.StrategyDraft;
import com.cryptolab.strategy.domain.UserStrategy;
import com.cryptolab.strategy.domain.UserStrategyDocument;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-strategies")
public final class StrategyAuthoringController {

    private final StrategyAuthoringService service;
    private final UserStrategyService userStrategies;

    public StrategyAuthoringController(
            StrategyAuthoringService service, UserStrategyService userStrategies) {
        this.service = service;
        this.userStrategies = userStrategies;
    }

    @PostMapping
    ResponseEntity<UserStrategy> save(
            @RequestBody UserStrategyDocument document, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userStrategies.save(account(request).id(), document));
    }

    @PostMapping("/drafts")
    ResponseEntity<StrategyDraft> propose(
            @RequestBody StrategyPromptRequest request, HttpServletRequest servletRequest) {
        StrategyDraft draft = service.propose(account(servletRequest).id(), request.prompt());
        return ResponseEntity.status(HttpStatus.CREATED).body(draft);
    }

    @PostMapping("/drafts/{draftId}/confirm")
    ResponseEntity<UserStrategy> confirm(
            @PathVariable UUID draftId, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.confirm(account(servletRequest).id(), draftId));
    }

    @GetMapping
    List<UserStrategy> list(HttpServletRequest request) {
        return userStrategies.list(account(request).id());
    }

    @GetMapping("/{strategyId}")
    UserStrategy get(@PathVariable UUID strategyId, HttpServletRequest request) {
        return userStrategies.get(account(request).id(), strategyId);
    }

    @DeleteMapping("/{strategyId}")
    ResponseEntity<Void> delete(@PathVariable UUID strategyId, HttpServletRequest request) {
        userStrategies.delete(account(request).id(), strategyId);
        return ResponseEntity.noContent().build();
    }

    private static AuthenticatedAccount account(HttpServletRequest request) {
        return AuthenticatedAccount.require(request.getSession(false));
    }
}
