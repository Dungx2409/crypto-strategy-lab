ALTER TABLE news_items
    ADD COLUMN content TEXT,
    ADD COLUMN crawled_at TIMESTAMPTZ,
    ADD COLUMN related_coins TEXT[] NOT NULL DEFAULT '{}';

UPDATE news_items
SET content = normalized_text,
    crawled_at = created_at
WHERE content IS NULL OR crawled_at IS NULL;

ALTER TABLE news_items
    ALTER COLUMN content SET NOT NULL,
    ALTER COLUMN crawled_at SET NOT NULL;
