CREATE TABLE crawler_templates (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    site_url VARCHAR(2000) NOT NULL,
    active_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE crawler_template_versions (
    template_id UUID NOT NULL REFERENCES crawler_templates(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    item_selector VARCHAR(500) NOT NULL,
    title_selector VARCHAR(500) NOT NULL,
    link_selector VARCHAR(500) NOT NULL,
    date_selector VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    repair_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (template_id, version)
);

CREATE INDEX idx_crawler_templates_account ON crawler_templates(account_id, created_at DESC);
