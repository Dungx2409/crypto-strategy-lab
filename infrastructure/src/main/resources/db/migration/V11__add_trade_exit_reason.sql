ALTER TABLE trades
    ADD COLUMN exit_reason varchar(16) NOT NULL DEFAULT 'SIGNAL';

ALTER TABLE trades
    ADD CONSTRAINT ck_trades_exit_reason
        CHECK (exit_reason IN ('SIGNAL', 'STOP_LOSS', 'TAKE_PROFIT', 'DATASET_END'));
