package com.cryptolab.news.port;

import com.cryptolab.news.domain.NewsItem;
import java.time.Instant;
import java.util.List;

public interface NewsProvider {
    List<NewsItem> fetchSince(Instant since);
}
