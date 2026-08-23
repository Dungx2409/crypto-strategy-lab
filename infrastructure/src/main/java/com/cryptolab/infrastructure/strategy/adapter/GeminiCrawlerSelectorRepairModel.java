package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.news.domain.CrawlerSelectors;
import com.cryptolab.news.port.CrawlerSelectorRepairModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class GeminiCrawlerSelectorRepairModel implements CrawlerSelectorRepairModel {
    private final GeminiStrategyAuthoringModel gemini;
    private final ObjectMapper objectMapper;

    public GeminiCrawlerSelectorRepairModel(
            GeminiStrategyAuthoringModel gemini, ObjectMapper objectMapper) {
        this.gemini = gemini;
        this.objectMapper = objectMapper;
    }

    @Override
    public CrawlerSelectors repair(
            String siteUrl, CrawlerSelectors previous, String sampleHtml, String failure) {
        String output = gemini.generateText("""
                Repair CSS selectors for a news page after its HTML changed.
                Return one JSON object only with itemSelector, titleSelector, linkSelector, and dateSelector.
                Do not return code or Markdown.
                Site: %s
                Previous selectors: %s
                Crawler failure: %s
                Current HTML sample: %s
                """.formatted(siteUrl, json(previous), failure, sampleHtml));
        try {
            if (!output.stripLeading().startsWith("{")) {
                throw new IllegalArgumentException("Gemini selector repair must return JSON without Markdown");
            }
            return objectMapper.readValue(output, CrawlerSelectors.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Gemini returned invalid selector JSON", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Crawler selectors could not be serialized", exception);
        }
    }
}
