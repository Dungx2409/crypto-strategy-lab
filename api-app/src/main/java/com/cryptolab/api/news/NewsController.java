package com.cryptolab.api.news;

import com.cryptolab.news.application.NewsCollector;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
public final class NewsController {

    private final NewsCollector collector;

    public NewsController(NewsCollector collector) {
        this.collector = collector;
    }

    @GetMapping
    public NewsResponse latest(@RequestParam(defaultValue = "20") int limit) {
        List<NewsItemResponse> items = collector.latest(limit).stream()
                .map(NewsItemResponse::from)
                .toList();
        return NewsResponse.from(collector.health(), items);
    }

    @PostMapping("/collect")
    public ResponseEntity<NewsCollectionResponse> collect() {
        return ResponseEntity.ok(NewsCollectionResponse.from(collector.collect()));
    }
}
