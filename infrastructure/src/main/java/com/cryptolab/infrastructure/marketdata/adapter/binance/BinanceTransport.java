package com.cryptolab.infrastructure.marketdata.adapter.binance;

import com.cryptolab.marketdata.port.MarketSubscription;
import java.net.URI;
import java.util.function.Consumer;

interface BinanceTransport {

    String get(URI uri);

    MarketSubscription connect(
            URI uri,
            Runnable onConnected,
            Consumer<String> onMessage,
            Consumer<Throwable> onDisconnected);
}
