package com.cryptolab.api.news;

import com.cryptolab.news.application.NewsCollector;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
public final class NewsController {

    private final NewsCollector collector;
    private final MutableNewsFeedPreferences preferences;
    private final NewsCollectionScheduler scheduler;

    public NewsController(
            NewsCollector collector,
            MutableNewsFeedPreferences preferences,
            NewsCollectionScheduler scheduler) {
        this.collector = collector;
        this.preferences = preferences;
        this.scheduler = scheduler;
    }

    @GetMapping
    public NewsResponse latest(@RequestParam(defaultValue = "20") int limit) {
        List<NewsItemResponse> items = collector.latest(limit).stream()
                .map(NewsItemResponse::from)
                .toList();
        return NewsResponse.from(collector.providerName(), collector.health(), items);
    }

    @GetMapping("/preferences")
    public NewsPreferencesResponse preferences() {
        return preferences.snapshot();
    }

    @PutMapping("/preferences")
    public NewsPreferencesResponse updatePreferences(@RequestBody NewsPreferencesRequest body) {
        NewsPreferencesResponse updated = preferences.update(
                body == null ? null : body.interval(),
                body == null ? null : body.coin(),
                body == null ? null : body.provider());
        scheduler.reschedule(preferences.intervalDuration());
        return updated;
    }

    @PostMapping("/collect")
    public ResponseEntity<NewsCollectionResponse> collect() {
        return ResponseEntity.ok(NewsCollectionResponse.from(collector.collectRecent()));
    }

    @PostMapping("/analyze")
    public ResponseEntity<NewsCollectionResponse> analyzePending(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(NewsCollectionResponse.from(collector.analyzePending(limit)));
    }
}
