package com.cryptolab.api.news;

import com.cryptolab.api.account.AuthenticatedAccount;
import com.cryptolab.news.application.CrawlerTemplateService;
import com.cryptolab.news.domain.CrawlerTemplateVersion;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crawler-templates")
public final class CrawlerTemplateController {
    private final CrawlerTemplateService service;

    public CrawlerTemplateController(CrawlerTemplateService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CrawlerTemplateVersion create(@RequestBody CrawlerTemplateRequest body, HttpServletRequest request) {
        return service.create(account(request).id(), body.siteUrl(), body.selectors());
    }

    @GetMapping
    List<CrawlerTemplateVersion> list(HttpServletRequest request) {
        return service.list(account(request).id());
    }

    @GetMapping("/{templateId}/versions")
    List<CrawlerTemplateVersion> versions(@PathVariable UUID templateId, HttpServletRequest request) {
        return service.versions(account(request).id(), templateId);
    }

    @PostMapping("/{templateId}/repair")
    CrawlerTemplateVersion repair(
            @PathVariable UUID templateId,
            @RequestBody CrawlerRepairRequest body,
            HttpServletRequest request) {
        return service.repair(account(request).id(), templateId, body.sampleHtml(), body.failure());
    }

    @PostMapping("/{templateId}/versions/{version}/confirm")
    CrawlerTemplateVersion confirm(
            @PathVariable UUID templateId, @PathVariable int version, HttpServletRequest request) {
        return service.confirm(account(request).id(), templateId, version);
    }

    @PostMapping("/{templateId}/check")
    CrawlerTemplateVersion check(@PathVariable UUID templateId, HttpServletRequest request) {
        return service.check(account(request).id(), templateId);
    }

    private static AuthenticatedAccount account(HttpServletRequest request) {
        return AuthenticatedAccount.require(request.getSession(false));
    }
}
