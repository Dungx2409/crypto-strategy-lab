CREATE TABLE market_dataset_sentiment_observations (
    dataset_id uuid NOT NULL REFERENCES market_datasets(id),
    sequence_no integer NOT NULL,
    source_id varchar(255) NOT NULL,
    observed_at timestamptz NOT NULL,
    score numeric(38, 18) NOT NULL,
    model_name varchar(128) NOT NULL,
    model_version varchar(128) NOT NULL,
    input_version varchar(128) NOT NULL,
    preprocessing_version varchar(128) NOT NULL,
    PRIMARY KEY (dataset_id, sequence_no),
    UNIQUE (dataset_id, source_id, model_name, model_version, input_version, preprocessing_version),
    CONSTRAINT ck_market_dataset_sentiment_score CHECK (score BETWEEN -1 AND 1)
);

CREATE INDEX idx_market_dataset_sentiment_time
    ON market_dataset_sentiment_observations(dataset_id, observed_at);
