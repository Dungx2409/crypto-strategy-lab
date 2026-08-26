CREATE TABLE crawler_sources (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    list_url TEXT NOT NULL,
    article_selector VARCHAR(500) NOT NULL,
    title_selector VARCHAR(500) NOT NULL,
    link_selector VARCHAR(500) NOT NULL,
    content_selector VARCHAR(500) NOT NULL,
    published_at_selector VARCHAR(500) NOT NULL,
    related_coins_selector VARCHAR(500) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT true,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    consecutive_failures INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
