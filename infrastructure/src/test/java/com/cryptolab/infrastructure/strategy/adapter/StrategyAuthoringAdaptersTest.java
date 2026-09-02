package com.cryptolab.infrastructure.strategy.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyAuthoringAdaptersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void blankGeminiKeyFailsClearlyWithoutMakingANetworkRequest() {
        var model = new GeminiStrategyAuthoringModel(
                java.net.http.HttpClient.newHttpClient(), objectMapper, "", "gemini-2.5-flash", Duration.ofSeconds(1));

        assertThatThrownBy(() -> model.proposeIdea("trend strategy", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY is blank");
    }

    @Test
    void decoderAcceptsRestrictedJsonAndRejectsMarkdownCodeBlocks() {
        var decoder = new JacksonStrategyDocumentDecoder(objectMapper);
        String json = """
                {"name":"Trend","description":"MA crossover",
                 "strategies":[{"type":"MOVING_AVERAGE","version":"1.0","parameters":{}}],
                 "combinationPolicy":{"type":"MAJORITY","version":"1.0","weights":{},"threshold":0}}
                """;

        assertThat(decoder.decode(json).name()).isEqualTo("Trend");
        assertThatThrownBy(() -> decoder.decode("```json\n" + json + "\n```"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without Markdown");
    }

    @Test
    void articleReaderRejectsPrivateAndNonHttpAddressesBeforeDownloading() {
        var reader = new HttpArticleSourceReader();

        assertThatThrownBy(() -> reader.read("http://127.0.0.1/article"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private address");
        assertThatThrownBy(() -> reader.read("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public HTTP or HTTPS URL");
    }
}
