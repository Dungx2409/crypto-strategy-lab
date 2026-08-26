CREATE TABLE crawler_source_versions (
    source_id UUID NOT NULL REFERENCES crawler_sources(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    name VARCHAR(100) NOT NULL,
    list_url TEXT NOT NULL,
    article_selector VARCHAR(500) NOT NULL,
    title_selector VARCHAR(500) NOT NULL,
    link_selector VARCHAR(500) NOT NULL,
    content_selector VARCHAR(500) NOT NULL,
    published_at_selector VARCHAR(500) NOT NULL,
    related_coins_selector VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(source_id, version)
);

INSERT INTO crawler_source_versions (
    source_id, version, name, list_url, article_selector, title_selector,
    link_selector, content_selector, published_at_selector,
    related_coins_selector, enabled, created_at
)
SELECT id, version, name, list_url, article_selector, title_selector,
       link_selector, content_selector, published_at_selector,
       related_coins_selector, enabled, updated_at
FROM crawler_sources;
