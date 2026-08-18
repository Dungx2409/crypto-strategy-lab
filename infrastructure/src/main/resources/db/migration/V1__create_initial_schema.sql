CREATE TABLE candles (
    symbol varchar(32) NOT NULL,
    timeframe varchar(8) NOT NULL,
    open_time timestamptz NOT NULL,
    open numeric(38, 18) NOT NULL,
    high numeric(38, 18) NOT NULL,
    low numeric(38, 18) NOT NULL,
    close numeric(38, 18) NOT NULL,
    volume numeric(38, 18) NOT NULL,
    provider varchar(64) NOT NULL,
    received_at timestamptz NOT NULL,
    PRIMARY KEY (symbol, timeframe, open_time),
    CONSTRAINT candles_price_bounds CHECK (high >= low),
    CONSTRAINT candles_volume_nonnegative CHECK (volume >= 0)
);

CREATE TABLE market_datasets (
    id uuid PRIMARY KEY,
    symbol varchar(32) NOT NULL,
    timeframe varchar(8) NOT NULL,
    from_time timestamptz NOT NULL,
    to_time timestamptz NOT NULL,
    dataset_version varchar(128) NOT NULL,
    checksum varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT market_datasets_range CHECK (from_time < to_time),
    UNIQUE (symbol, timeframe, from_time, to_time, dataset_version, checksum)
);

CREATE TABLE market_dataset_candles (
    dataset_id uuid NOT NULL REFERENCES market_datasets(id),
    sequence_no integer NOT NULL,
    open_time timestamptz NOT NULL,
    open numeric(38, 18) NOT NULL,
    high numeric(38, 18) NOT NULL,
    low numeric(38, 18) NOT NULL,
    close numeric(38, 18) NOT NULL,
    volume numeric(38, 18) NOT NULL,
    PRIMARY KEY (dataset_id, sequence_no),
    UNIQUE (dataset_id, open_time)
);

CREATE TABLE search_runs (
    id uuid PRIMARY KEY,
    status varchar(32) NOT NULL,
    symbol varchar(32) NOT NULL,
    timeframe varchar(8) NOT NULL,
    generator_type varchar(64) NOT NULL,
    generator_version varchar(64) NOT NULL,
    random_seed bigint,
    search_config_json jsonb NOT NULL,
    stop_conditions_json jsonb NOT NULL,
    execution_config_json jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    started_at timestamptz,
    ended_at timestamptz,
    cancel_requested boolean NOT NULL DEFAULT false
);

CREATE TABLE candidates (
    id uuid PRIMARY KEY,
    search_run_id uuid NOT NULL REFERENCES search_runs(id),
    candidate_hash varchar(128) NOT NULL,
    candidate_spec_json jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (search_run_id, candidate_hash)
);

CREATE TABLE experiments (
    id uuid PRIMARY KEY,
    candidate_id uuid NOT NULL REFERENCES candidates(id),
    search_run_id uuid NOT NULL REFERENCES search_runs(id),
    status varchar(32) NOT NULL,
    dataset_ref_json jsonb NOT NULL,
    execution_config_json jsonb NOT NULL,
    strategy_snapshot_json jsonb NOT NULL,
    combination_policy_json jsonb NOT NULL,
    generator_snapshot_json jsonb NOT NULL,
    evaluator_version varchar(64) NOT NULL,
    code_commit varchar(128) NOT NULL,
    build_version varchar(128) NOT NULL,
    reproduction_of_id uuid REFERENCES experiments(id),
    started_at timestamptz,
    completed_at timestamptz,
    failure_code varchar(64),
    failure_message text,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE experiment_signals (
    id uuid PRIMARY KEY,
    experiment_id uuid NOT NULL REFERENCES experiments(id),
    sequence_no integer NOT NULL,
    strategy_type varchar(64) NOT NULL,
    signal_type varchar(16) NOT NULL,
    strength numeric(38, 18) NOT NULL,
    signal_at timestamptz NOT NULL,
    reason text NOT NULL,
    UNIQUE (experiment_id, sequence_no)
);

CREATE TABLE trades (
    id uuid PRIMARY KEY,
    experiment_id uuid NOT NULL REFERENCES experiments(id),
    sequence_no integer NOT NULL,
    entry_time timestamptz NOT NULL,
    entry_price numeric(38, 18) NOT NULL,
    exit_time timestamptz NOT NULL,
    exit_price numeric(38, 18) NOT NULL,
    quantity numeric(38, 18) NOT NULL,
    fee numeric(38, 18) NOT NULL,
    pnl numeric(38, 18) NOT NULL,
    UNIQUE (experiment_id, sequence_no)
);

CREATE TABLE evaluation_metrics (
    experiment_id uuid PRIMARY KEY REFERENCES experiments(id),
    total_return_pct numeric(38, 18) NOT NULL,
    max_drawdown_pct numeric(38, 18) NOT NULL,
    total_trades integer NOT NULL,
    score numeric(38, 18) NOT NULL,
    metrics_json jsonb NOT NULL
);

CREATE TABLE leaderboard_entries (
    search_run_id uuid NOT NULL REFERENCES search_runs(id),
    experiment_id uuid NOT NULL REFERENCES experiments(id),
    rank integer NOT NULL,
    score numeric(38, 18) NOT NULL,
    return_pct numeric(38, 18) NOT NULL,
    max_drawdown_pct numeric(38, 18) NOT NULL,
    total_trades integer NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (search_run_id, experiment_id),
    CONSTRAINT leaderboard_rank_positive CHECK (rank > 0)
);

CREATE TABLE news_items (
    news_id varchar(256) PRIMARY KEY,
    provider varchar(64) NOT NULL,
    title text NOT NULL,
    url text NOT NULL,
    published_at timestamptz NOT NULL,
    normalized_text text NOT NULL,
    input_version varchar(64) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE sentiment_predictions (
    id uuid PRIMARY KEY,
    news_id varchar(256) NOT NULL REFERENCES news_items(news_id),
    sentiment varchar(16) NOT NULL,
    score numeric(38, 18) NOT NULL,
    model_name varchar(128) NOT NULL,
    model_version varchar(64) NOT NULL,
    input_version varchar(64) NOT NULL,
    preprocessing_version varchar(64) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE outbox_events (
    event_id uuid PRIMARY KEY,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type varchar(128) NOT NULL,
    schema_version integer NOT NULL,
    payload_json jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    last_error text,
    CONSTRAINT outbox_schema_version_positive CHECK (schema_version > 0),
    CONSTRAINT outbox_attempt_count_nonnegative CHECK (attempt_count >= 0)
);

CREATE TABLE processed_events (
    consumer_name varchar(128) NOT NULL,
    event_id uuid NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE INDEX idx_candles_symbol_timeframe_open_time
    ON candles (symbol, timeframe, open_time DESC);
CREATE INDEX idx_candidates_search_run ON candidates (search_run_id);
CREATE INDEX idx_experiments_search_status ON experiments (search_run_id, status);
CREATE INDEX idx_leaderboard_search_rank ON leaderboard_entries (search_run_id, rank);
CREATE INDEX idx_news_items_published_at ON news_items (published_at DESC);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
