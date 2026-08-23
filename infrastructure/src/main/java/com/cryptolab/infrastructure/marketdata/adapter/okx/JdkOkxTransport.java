package com.cryptolab.infrastructure.marketdata.adapter.okx;

import com.cryptolab.marketdata.port.MarketSubscription;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class JdkOkxTransport implements OkxTransport {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    JdkOkxTransport(Duration connectTimeout, Duration requestTimeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OKX returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("OKX HTTP request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OKX HTTP request was interrupted", exception);
        }
    }

    @Override
    public MarketSubscription connect(
            URI uri,
            String subscriptionMessage,
            Runnable onConnected,
            Consumer<String> onMessage,
            Consumer<Throwable> onDisconnected) {
        AtomicBoolean desired = new AtomicBoolean(true);
        AtomicBoolean terminated = new AtomicBoolean();
        AtomicReference<WebSocket> socket = new AtomicReference<>();
        StringBuilder fragments = new StringBuilder();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                socket.set(webSocket);
                webSocket.sendText(subscriptionMessage, true);
                onConnected.run();
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                fragments.append(data);
                if (last) {
                    String message = fragments.toString();
                    fragments.setLength(0);
                    onMessage.accept(message);
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                notifyDisconnected(new IllegalStateException(
                        "OKX WebSocket closed with status " + statusCode + ": " + reason));
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                notifyDisconnected(error);
            }

            private void notifyDisconnected(Throwable cause) {
                if (desired.get() && terminated.compareAndSet(false, true)) {
                    onDisconnected.accept(cause);
                }
            }
        };

        httpClient.newWebSocketBuilder()
                .connectTimeout(requestTimeout)
                .buildAsync(uri, listener)
                .whenComplete((webSocket, failure) -> {
                    if (webSocket != null) {
                        socket.set(webSocket);
                    }
                    if (failure != null
                            && desired.get()
                            && terminated.compareAndSet(false, true)) {
                        onDisconnected.accept(failure);
                    }
                });

        return new MarketSubscription() {
            @Override
            public boolean isActive() {
                return desired.get() && !terminated.get();
            }

            @Override
            public void close() {
                if (desired.compareAndSet(true, false)) {
                    WebSocket webSocket = socket.get();
                    if (webSocket != null) {
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "subscription released");
                    }
                }
            }
        };
    }
}
