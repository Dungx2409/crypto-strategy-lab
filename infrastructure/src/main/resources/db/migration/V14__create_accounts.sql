CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    normalized_username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(72) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
