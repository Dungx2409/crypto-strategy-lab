CREATE TABLE strategy_drafts (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    prompt VARCHAR(4000) NOT NULL,
    idea TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_strategy_drafts_account ON strategy_drafts(account_id, created_at DESC);

CREATE TABLE user_strategies (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    document_json JSONB NOT NULL,
    source_prompt VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (account_id, normalized_name, version)
);

CREATE INDEX idx_user_strategies_account ON user_strategies(account_id, normalized_name, version DESC);
