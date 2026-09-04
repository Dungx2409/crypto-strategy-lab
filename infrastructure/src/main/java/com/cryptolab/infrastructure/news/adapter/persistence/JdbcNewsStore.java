package com.cryptolab.infrastructure.news.adapter.persistence;

import com.cryptolab.news.domain.ModelDescriptor;
import com.cryptolab.news.domain.NewsInsight;
import com.cryptolab.news.domain.NewsItem;
import com.cryptolab.news.domain.SentimentLabel;
import com.cryptolab.news.domain.SentimentResult;
import com.cryptolab.news.port.NewsStore;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcNewsStore implements NewsStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNewsStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int saveNewsItems(List<NewsItem> items, Instant storedAt) {
        int stored = 0;
        for (NewsItem item : List.copyOf(items)) {
            stored += jdbcTemplate.update(
                    """
                    INSERT INTO news_items (
                        news_id, provider, title, url, published_at,
                        normalized_text, input_version, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (news_id) DO UPDATE
                    SET provider = EXCLUDED.provider,
                        title = EXCLUDED.title,
                        url = EXCLUDED.url,
                        published_at = EXCLUDED.published_at,
                        normalized_text = EXCLUDED.normalized_text,
                        input_version = EXCLUDED.input_version
                    """,
                    item.newsId(),
                    item.provider(),
                    item.title(),
                    item.url(),
                    timestamp(item.publishedAt()),
                    item.normalizedText(),
                    item.inputVersion(),
                    timestamp(storedAt));
        }
        return stored;
    }

    @Override
    public boolean hasPrediction(
            String newsId,
            String inputVersion,
            ModelDescriptor model,
            String preprocessingVersion) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM sentiment_predictions
                WHERE news_id = ? AND input_version = ?
                  AND model_name = ? AND model_version = ?
                  AND preprocessing_version = ?
                """,
                Integer.class,
                newsId,
                inputVersion,
                model.name(),
                model.version(),
                preprocessingVersion);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void saveSentiment(SentimentResult result) {
        jdbcTemplate.update(
                """
                INSERT INTO sentiment_predictions (
                    id, news_id, sentiment, score, model_name, model_version,
                    input_version, preprocessing_version, created_at, summary
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (news_id, model_name, model_version, input_version, preprocessing_version)
                DO NOTHING
                """,
                predictionId(result),
                result.newsId(),
                result.sentiment().name(),
                result.score(),
                result.model().name(),
                result.model().version(),
                result.inputVersion(),
                result.preprocessingVersion(),
                timestamp(result.createdAt()),
                result.summary());
    }

    @Override
    public List<NewsInsight> findLatest(int limit) {
        return jdbcTemplate.query(
                """
                SELECT n.news_id, n.provider, n.title, n.url, n.published_at,
                       n.normalized_text, n.input_version,
                       p.sentiment, p.score, p.model_name, p.model_version,
                       p.prediction_input_version, p.preprocessing_version,
                       p.prediction_created_at, p.summary
                FROM news_items n
                LEFT JOIN LATERAL (
                    SELECT sentiment, score, model_name, model_version,
                           input_version AS prediction_input_version,
                           preprocessing_version, created_at AS prediction_created_at,
                           summary
                    FROM sentiment_predictions
                    WHERE news_id = n.news_id AND input_version = n.input_version
                    ORDER BY created_at DESC, id
                    LIMIT 1
                ) p ON true
                ORDER BY n.published_at DESC, n.news_id
                LIMIT ?
                """,
                JdbcNewsStore::insight,
                limit);
    }

    @Override
    public Optional<Instant> latestPublishedAt() {
        List<Instant> values = jdbcTemplate.query(
                "SELECT MAX(published_at) AS latest FROM news_items",
                (resultSet, rowNumber) -> {
                    OffsetDateTime value = resultSet.getObject("latest", OffsetDateTime.class);
                    return value == null ? null : value.toInstant();
                });
        return values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.getFirst());
    }

    private static NewsInsight insight(ResultSet resultSet, int rowNumber) throws SQLException {
        NewsItem item = new NewsItem(
                resultSet.getString("news_id"),
                resultSet.getString("provider"),
                resultSet.getString("title"),
                resultSet.getString("url"),
                resultSet.getObject("published_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("normalized_text"),
                resultSet.getString("input_version"));
        String sentiment = resultSet.getString("sentiment");
        if (sentiment == null) {
            return new NewsInsight(item, Optional.empty());
        }
        SentimentResult prediction = new SentimentResult(
                item.newsId(),
                SentimentLabel.valueOf(sentiment),
                resultSet.getBigDecimal("score"),
                new ModelDescriptor(
                        resultSet.getString("model_name"),
                        resultSet.getString("model_version")),
                resultSet.getString("prediction_input_version"),
                resultSet.getString("preprocessing_version"),
                resultSet.getObject("prediction_created_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("summary"));
        return new NewsInsight(item, Optional.of(prediction));
    }

    private static UUID predictionId(SentimentResult result) {
        String identity = String.join(
                ":",
                result.newsId(),
                result.model().name(),
                result.model().version(),
                result.inputVersion(),
                result.preprocessingVersion());
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
