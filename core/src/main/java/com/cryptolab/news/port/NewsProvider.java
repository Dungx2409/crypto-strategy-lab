package com.cryptolab.news.port;

import com.cryptolab.news.domain.NewsItem;
import java.time.Instant;
import java.util.List;

public interface NewsProvider {
    List<NewsItem> fetchSince(Instant since);

    /**
     * @param categoriesCsv CryptoCompare-style categories such as {@code BTC} or {@code BTC,ETH};
     *                      blank means no category filter
     */
    default List<NewsItem> fetchSince(Instant since, String categoriesCsv) {
        return fetchSince(since);
    }
}
