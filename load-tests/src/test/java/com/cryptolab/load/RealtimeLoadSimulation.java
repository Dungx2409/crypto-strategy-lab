package com.cryptolab.load;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

public final class RealtimeLoadSimulation extends Simulation {

    private static final int USERS = Integer.getInteger("load.users", 1000);
    private static final Duration RAMP = Duration.ofSeconds(Long.getLong("load.rampSeconds", 120));
    private static final Duration HOLD = Duration.ofSeconds(Long.getLong("load.holdSeconds", 600));
    private static final String BASE_URL = System.getProperty("load.baseUrl", "http://localhost:8080");
    private static final String WS_URL = BASE_URL.replaceFirst("^http", "ws");

    private static String subscribe(int index, String timeframe) {
        return "SUBSCRIBE\nid:market-" + index
                + "\ndestination:/topic/market/BTCUSDT/" + timeframe
                + "\nack:auto\n\n\u0000";
    }

    {
        var connected = ws.checkTextMessage("stomp-connected")
                .check(substring("CONNECTED"));
        var candle = ws.checkTextMessage("first-candle-per-stream")
                .check(substring("CANDLE_UPDATE"));

        var scenario = scenario("1,000 sessions with four realtime charts")
                .exec(ws("websocket-connect").connect("/ws"))
                .exec(ws("stomp-connect")
                        .sendText("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:10000,10000\n\n\u0000")
                        .await(10).on(connected))
                .exec(ws("subscribe-5m").sendText(subscribe(0, "5m")).await(30).on(candle))
                .exec(ws("subscribe-15m").sendText(subscribe(1, "15m")).await(30).on(candle))
                .exec(ws("subscribe-1h").sendText(subscribe(2, "1h")).await(30).on(candle))
                .exec(ws("subscribe-4h").sendText(subscribe(3, "4h")).await(30).on(candle))
                .pause(HOLD)
                .exec(ws("websocket-close").close());

        setUp(scenario.injectOpen(rampUsers(USERS).during(RAMP)))
                .protocols(http.baseUrl(BASE_URL).wsBaseUrl(WS_URL))
                .assertions(
                        global().failedRequests().percent().lte(1.0),
                        details("websocket-connect").successfulRequests().percent().gte(99.0),
                        details("first-candle-per-stream").successfulRequests().percent().gte(99.0),
                        details("first-candle-per-stream").responseTime().percentile(95).lt(1000),
                        global().requestsPerSec().gt(0.0));
    }
}
