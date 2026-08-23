package com.cryptolab.infrastructure.marketdata.adapter.okx;

record OkxCandleDto(
        long openTime,
        String open,
        String high,
        String low,
        String close,
        String volume,
        boolean closed) {}
