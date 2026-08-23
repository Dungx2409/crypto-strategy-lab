ALTER TABLE trades
    ADD COLUMN direction varchar(8) NOT NULL DEFAULT 'LONG';

ALTER TABLE trades
    ADD CONSTRAINT ck_trades_direction
        CHECK (direction IN ('LONG', 'SHORT'));
