package com.cryptolab.infrastructure.marketdata.adapter.okx;

import com.cryptolab.marketdata.port.MarketSubscription;
import java.net.URI;
import java.util.function.Consumer;

interface OkxTransport {

    String get(URI uri);

    MarketSubscription connect(
            URI uri,
            String subscriptionMessage,
            Runnable onConnected,
            Consumer<String> onMessage,
            Consumer<Throwable> onDisconnected);
}
