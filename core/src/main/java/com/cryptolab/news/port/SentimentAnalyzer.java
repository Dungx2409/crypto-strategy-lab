package com.cryptolab.news.port;

import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.SentimentResult;

public interface SentimentAnalyzer {

    ModelDescriptor descriptor();

    String preprocessingVersion();

    SentimentResult analyze(NewsItem item);
}
