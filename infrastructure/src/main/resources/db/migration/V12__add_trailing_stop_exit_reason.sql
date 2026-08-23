ALTER TABLE trades
    DROP CONSTRAINT ck_trades_exit_reason;

ALTER TABLE trades
    ADD CONSTRAINT ck_trades_exit_reason
        CHECK (exit_reason IN ('SIGNAL', 'STOP_LOSS', 'TRAILING_STOP', 'TAKE_PROFIT', 'DATASET_END'));
