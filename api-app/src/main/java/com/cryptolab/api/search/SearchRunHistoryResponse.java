package com.cryptolab.api.search;

import com.cryptolab.experiment.domain.SearchRunSummary;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public record SearchRunHistoryResponse(List<SearchRunResponse> items, String nextCursor) {

    static SearchRunHistoryResponse from(List<SearchRunSummary> summaries, int requestedLimit) {
        List<SearchRunResponse> items = summaries.stream().map(SearchRunResponse::from).toList();
        String cursor = summaries.size() < requestedLimit
                ? null
                : encode(summaries.getLast());
        return new SearchRunHistoryResponse(items, cursor);
    }

    private static String encode(SearchRunSummary summary) {
        String value = summary.run().createdAt().toEpochMilli() + ":" + summary.run().id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
