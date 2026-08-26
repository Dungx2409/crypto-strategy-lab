ALTER TABLE accounts
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';

ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_role CHECK (role IN ('USER', 'ADMIN'));
