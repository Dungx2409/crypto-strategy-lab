import ws from "k6/ws";
import {check} from "k6";
import {Counter, Rate, Trend} from "k6/metrics";
import {marketTimeframe, stompFrameText} from "./stomp-frame.mjs";

export const options = {
    scenarios: {
        realtime_1000: {
            executor: "per-vu-iterations",
            vus: Number(__ENV.VUS || 1000),
            iterations: 1,
            maxDuration: "3m"
        }
    },
    thresholds: {
        checks: ["rate>0.99"],
        ws_connecting: ["p(95)<3000"],
        candle_updates: ["count>0"],
        complete_realtime_sessions: ["rate>0.99"]
    }
};

const updates = new Counter("candle_updates");
const firstUpdate = new Trend("first_update_ms");
const completeSessions = new Rate("complete_realtime_sessions");
const baseUrl = __ENV.WS_URL || "ws://localhost:8080/ws";
const sessionSeconds = Number(__ENV.SESSION_SECONDS || 30);
const timeframes = ["5m", "15m", "1h", "4h"];

export default function () {
    const started = Date.now();
    const received = new Set();
    let connected = false;
    let firstUpdateRecorded = false;
    const result = ws.connect(baseUrl, {}, socket => {
        socket.on("open", () => socket.send("CONNECT\naccept-version:1.2\nhost:localhost:8080\nheart-beat:10000,10000\n\n\u0000"));
        socket.on("message", message => {
            const frame = stompFrameText(message);
            if (frame.startsWith("CONNECTED")) {
                connected = true;
                timeframes.forEach((timeframe, index) => socket.send(
                    `SUBSCRIBE\nid:chart-${index}\ndestination:/topic/market/BTCUSDT/${timeframe}\nack:auto\n\n\u0000`));
            }
            if (frame.startsWith("MESSAGE")) {
                updates.add(1);
                const timeframe = marketTimeframe(frame);
                if (timeframe) received.add(timeframe);
                if (!firstUpdateRecorded) {
                    firstUpdate.add(Date.now() - started);
                    firstUpdateRecorded = true;
                }
            }
        });
        socket.setTimeout(() => socket.close(), sessionSeconds * 1000);
    });
    const complete = connected && timeframes.every(timeframe => received.has(timeframe));
    completeSessions.add(complete);
    check(result, {"WebSocket upgraded": response => response && response.status === 101});
    check(connected, {"STOMP connected": value => value});
    check(complete, {"all four chart topics delivered candles": value => value});
}
