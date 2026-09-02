ALTER TABLE candles DROP CONSTRAINT candles_pkey;

ALTER TABLE candles
    ADD PRIMARY KEY (provider, symbol, timeframe, open_time);

DROP INDEX idx_candles_symbol_timeframe_open_time;

CREATE INDEX idx_candles_provider_symbol_timeframe_open_time
    ON candles (provider, symbol, timeframe, open_time DESC);
