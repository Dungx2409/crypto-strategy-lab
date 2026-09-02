package com.cryptolab.strategy.application;

import com.cryptolab.experiment.port.CombinationPolicyResolver;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.marketdata.domain.TradingPair;
import com.cryptolab.strategy.domain.StrategyContext;
import com.cryptolab.strategy.domain.StrategyDraft;
import com.cryptolab.strategy.domain.StrategyDraftStatus;
import com.cryptolab.strategy.domain.UserStrategy;
import com.cryptolab.strategy.domain.UserStrategyDocument;
import com.cryptolab.strategy.domain.extension.AiDslStrategy;
import com.cryptolab.strategy.port.ArticleSourceReader;
import com.cryptolab.strategy.port.StrategyAuthoringModel;
import com.cryptolab.strategy.port.StrategyDocumentDecoder;
import com.cryptolab.strategy.port.StrategyRegistry;
import com.cryptolab.strategy.port.UserStrategyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class StrategyAuthoringService {

    private static final int MAX_JSON_ATTEMPTS = 3;
    private final StrategyAuthoringModel model;
    private final StrategyDocumentDecoder decoder;
    private final UserStrategyRepository repository;
    private final StrategyRegistry registry;
    private final CombinationPolicyResolver policyResolver;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final ArticleSourceReader articles;

    public StrategyAuthoringService(
            StrategyAuthoringModel model,
            StrategyDocumentDecoder decoder,
            UserStrategyRepository repository,
            StrategyRegistry registry,
            CombinationPolicyResolver policyResolver,
            Clock clock,
            Supplier<UUID> ids) {
        this(model, decoder, repository, registry, policyResolver, clock, ids,
                url -> { throw new IllegalStateException("Article authoring is not configured"); });
    }

    public StrategyAuthoringService(
            StrategyAuthoringModel model,
            StrategyDocumentDecoder decoder,
            UserStrategyRepository repository,
            StrategyRegistry registry,
            CombinationPolicyResolver policyResolver,
            Clock clock,
            Supplier<UUID> ids,
            ArticleSourceReader articles) {
        this.model = Objects.requireNonNull(model);
        this.decoder = Objects.requireNonNull(decoder);
        this.repository = Objects.requireNonNull(repository);
        this.registry = Objects.requireNonNull(registry);
        this.policyResolver = Objects.requireNonNull(policyResolver);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
        this.articles = Objects.requireNonNull(articles);
    }

    public StrategyDraft proposeFromArticle(UUID accountId, String articleUrl) {
        String article = articles.read(articleUrl);
        String prefix = "Create a strategy from this article. Source URL: " + articleUrl + "\n\n";
        int remaining = Math.max(0, 4000 - prefix.length());
        String prompt = prefix + article.substring(0, Math.min(article.length(), remaining));
        return propose(accountId, prompt);
    }

    public StrategyDraft propose(UUID accountId, String prompt) {
        String idea = model.proposeIdea(prompt, registry.authoringStrategies());
        Instant now = clock.instant();
        return repository.createDraft(new StrategyDraft(
                ids.get(), accountId, prompt, idea,
                StrategyDraftStatus.IDEA_PENDING_CONFIRMATION, null, null, now, now));
    }

    public StrategyDraft build(UUID accountId, UUID draftId) {
        StrategyDraft draft = repository.findDraft(accountId, draftId)
                .orElseThrow(() -> new StrategyDraftNotFoundException(draftId));
        if (draft.status() != StrategyDraftStatus.IDEA_PENDING_CONFIRMATION) {
            throw new IllegalStateException("strategy draft is not waiting for confirmation");
        }
        repository.updateDraft(
                accountId, draftId, StrategyDraftStatus.BUILDING, null, null, clock.instant());
        String previous = null;
        String error = null;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_JSON_ATTEMPTS; attempt++) {
            try {
                previous = model.generateJson(
                        draft.prompt(), draft.idea(), registry.authoringStrategies(), previous, error);
                UserStrategyDocument document = decoder.decode(previous);
                smokeTest(document);
                repository.updateDraft(
                        accountId,
                        draftId,
                        StrategyDraftStatus.CODE_READY_FOR_CONFIRMATION,
                        document,
                        null,
                        clock.instant());
                return repository.findDraft(accountId, draftId).orElseThrow();
            } catch (RuntimeException failure) {
                lastFailure = failure;
                error = safeMessage(failure);
            }
        }
        repository.updateDraft(
                accountId, draftId, StrategyDraftStatus.FAILED, null, error, clock.instant());
        throw new StrategyAuthoringFailedException(
                "Gemini could not produce a valid strategy after " + MAX_JSON_ATTEMPTS + " attempts",
                lastFailure);
    }

    public UserStrategy confirm(UUID accountId, UUID draftId) {
        StrategyDraft draft = repository.findDraft(accountId, draftId)
                .orElseThrow(() -> new StrategyDraftNotFoundException(draftId));
        if (draft.status() != StrategyDraftStatus.CODE_READY_FOR_CONFIRMATION) {
            throw new IllegalStateException("strategy draft does not have tested code awaiting confirmation");
        }
        return repository.publishVersion(ids.get(), accountId, draftId, clock.instant());
    }

    public List<UserStrategy> list(UUID accountId) {
        return repository.findAll(accountId);
    }

    public UserStrategy get(UUID accountId, UUID strategyId) {
        return repository.find(accountId, strategyId)
                .orElseThrow(() -> new IllegalArgumentException("User strategy was not found: " + strategyId));
    }

    public void delete(UUID accountId, UUID strategyId) {
        if (!repository.delete(accountId, strategyId)) {
            throw new IllegalArgumentException("User strategy was not found: " + strategyId);
        }
    }

    private void smokeTest(UserStrategyDocument document) {
        boolean hasGeneratedSource = document.strategies().stream()
                .anyMatch(definition -> definition.type().equals(AiDslStrategy.TYPE)
                        && definition.version().equals(AiDslStrategy.VERSION));
        if (!hasGeneratedSource) {
            throw new IllegalArgumentException("generated strategy must include AI_DSL@1.0 source");
        }
        var context = smokeContext();
        document.strategies().forEach(definition -> registry.create(definition).analyze(context));
        policyResolver.resolve(document.combinationPolicy());
    }

    private static StrategyContext smokeContext() {
        TradingPair pair = new TradingPair("BTCUSDT");
        Timeframe timeframe = Timeframe.H1;
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 250; index++) {
            BigDecimal open = BigDecimal.valueOf(30_000L + index);
            candles.add(new Candle(
                    pair.symbol(), timeframe, start.plus(timeframe.duration().multipliedBy(index)),
                    open, open.add(BigDecimal.TEN), open.subtract(BigDecimal.TEN),
                    open.add(BigDecimal.ONE), BigDecimal.valueOf(100)));
        }
        return new StrategyContext(pair, timeframe, candles, start.plus(timeframe.duration().multipliedBy(250)));
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
