package com.cryptolab.infrastructure.marketdata.adapter.binance;

record BinanceKlineDto(
        long openTime,
        String open,
        String high,
        String low,
        String close,
        String volume,
        boolean closed) {}
