package com.cryptolab.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.news.application.CrawlerTemplateService;
import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.domain.CrawlerTemplateVersion;
import com.cryptolab.news.port.CrawlerPageReader;
import com.cryptolab.news.port.CrawlerSelectorMatcher;
import com.cryptolab.news.port.CrawlerSelectorRepairModel;
import com.cryptolab.news.port.CrawlerTemplateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrawlerTemplateServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID TEMPLATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000043");
    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    @Test
    void createsReviewVersionWhenActiveSelectorsStopMatching() {
        CrawlerPageReader pages = url -> "<article class='story'><h2>Title</h2><a href='/story'>Read</a></article>";
        CrawlerSelectors currentSelectors = new CrawlerSelectors(".old", "h1", "a", "");
        CrawlerSelectors repairedSelectors = new CrawlerSelectors("article.story", "h2", "a", "");
        CrawlerTemplateVersion current = version(1, currentSelectors, "ACTIVE");
        CrawlerTemplateVersion pending = version(2, repairedSelectors, "NEEDS_REVIEW");
        RecordingRepository repository = new RecordingRepository(current, pending);
        CrawlerSelectorRepairModel repair = (siteUrl, selectors, sampleHtml, failure) -> repairedSelectors;
        CrawlerSelectorMatcher matcher = (html, selectors) -> selectors.equals(repairedSelectors);
        CrawlerTemplateService service = new CrawlerTemplateService(
                repository, repair, Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID, pages, matcher);

        assertThat(service.check(ACCOUNT_ID, TEMPLATE_ID)).isEqualTo(pending);
        assertThat(repository.addedSelectors).isEqualTo(repairedSelectors);
        assertThat(repository.addedReason).isEqualTo("Active selectors no longer match the current page");
    }

    private static CrawlerTemplateVersion version(int number, CrawlerSelectors selectors, String status) {
        return new CrawlerTemplateVersion(
                TEMPLATE_ID, ACCOUNT_ID, "https://news.example.com", number,
                selectors, status, null, NOW);
    }

    private static final class RecordingRepository implements CrawlerTemplateRepository {
        private final CrawlerTemplateVersion current;
        private final CrawlerTemplateVersion pending;
        private CrawlerSelectors addedSelectors;
        private String addedReason;

        private RecordingRepository(CrawlerTemplateVersion current, CrawlerTemplateVersion pending) {
            this.current = current;
            this.pending = pending;
        }

        @Override public Optional<CrawlerTemplateVersion> findCurrent(UUID accountId, UUID templateId) {
            return Optional.of(current);
        }

        @Override public List<CrawlerTemplateVersion> findVersions(UUID accountId, UUID templateId) {
            return List.of(current);
        }

        @Override public CrawlerTemplateVersion addRepair(
                UUID accountId, UUID templateId, CrawlerSelectors selectors, String reason, Instant at) {
            addedSelectors = selectors;
            addedReason = reason;
            return pending;
        }

        @Override public CrawlerTemplateVersion create(
                UUID id, UUID accountId, String siteUrl, CrawlerSelectors selectors, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override public List<CrawlerTemplateVersion> findCurrentByAccount(UUID accountId) {
            return List.of(current);
        }

        @Override public List<CrawlerTemplateVersion> findAllCurrent() {
            return List.of(current);
        }

        @Override public CrawlerTemplateVersion activate(
                UUID accountId, UUID templateId, int version, Instant at) {
            throw new UnsupportedOperationException();
        }
    }
}
