package com.cryptolab.api.strategy;

public record StrategyPromptRequest(String prompt, String articleUrl) {

    boolean usesArticle() {
        boolean hasPrompt = prompt != null && !prompt.isBlank();
        boolean hasArticle = articleUrl != null && !articleUrl.isBlank();
        if (hasPrompt == hasArticle) {
            throw new IllegalArgumentException("Provide exactly one of prompt or articleUrl");
        }
        return hasArticle;
    }
}
