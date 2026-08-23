import test from "node:test";
import assert from "node:assert/strict";
import {marketTimeframe, stompFrameText} from "./stomp-frame.mjs";

test("reads k6 message events and extracts a market timeframe", () => {
    const event = {data: "MESSAGE\ndestination:/topic/market/BTCUSDT/15m\n\n{}\0"};

    const frame = stompFrameText(event);

    assert.equal(frame, event.data);
    assert.equal(marketTimeframe(frame), "15m");
});

test("keeps legacy k6 string messages", () => {
    assert.equal(stompFrameText("CONNECTED\nversion:1.2\n\n\0"), "CONNECTED\nversion:1.2\n\n\0");
    assert.equal(marketTimeframe("CONNECTED\nversion:1.2\n\n\0"), null);
});
