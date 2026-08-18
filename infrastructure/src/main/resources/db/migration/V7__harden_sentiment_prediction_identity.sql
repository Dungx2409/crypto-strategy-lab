ALTER TABLE sentiment_predictions
    ADD CONSTRAINT sentiment_predictions_label_valid
        CHECK (sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE')),
    ADD CONSTRAINT sentiment_predictions_score_bounded
        CHECK (score >= -1 AND score <= 1),
    ADD CONSTRAINT sentiment_predictions_model_identity_unique
        UNIQUE (news_id, model_name, model_version, input_version, preprocessing_version);

CREATE INDEX idx_sentiment_predictions_news_created
    ON sentiment_predictions (news_id, created_at DESC);
