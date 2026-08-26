import test from "node:test";
import assert from "node:assert/strict";
import {simpleMovingAverage} from "../api-app/src/main/resources/static/chart-math.js";

test("simple moving average keeps candle timestamps and starts after one full window", () => {
    const candles = [1, 2, 3].map((close, index) => ({time: index + 1, close}));
    assert.deepEqual(simpleMovingAverage(candles, 2), [
        {time: 2, value: 1.5},
        {time: 3, value: 2.5}
    ]);
});
