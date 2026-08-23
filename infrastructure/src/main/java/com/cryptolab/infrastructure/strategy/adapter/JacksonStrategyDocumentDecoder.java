package com.cryptolab.infrastructure.strategy.adapter;

import com.cryptolab.strategy.domain.UserStrategyDocument;
import com.cryptolab.strategy.port.StrategyDocumentDecoder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class JacksonStrategyDocumentDecoder implements StrategyDocumentDecoder {

    private final ObjectMapper objectMapper;

    public JacksonStrategyDocumentDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public UserStrategyDocument decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Gemini returned an empty strategy document");
        }
        if (!json.stripLeading().startsWith("{")) {
            throw new IllegalArgumentException("Gemini response must be a JSON object without Markdown");
        }
        try {
            return objectMapper.readValue(json, UserStrategyDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Gemini returned invalid strategy JSON: " + exception.getOriginalMessage(), exception);
        }
    }
}
