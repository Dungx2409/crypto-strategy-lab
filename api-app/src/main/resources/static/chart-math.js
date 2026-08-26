export function simpleMovingAverage(candles, period) {
    const values = [];
    let sum = 0;
    candles.forEach((candle, index) => {
        sum += Number(candle.close);
        if (index >= period) sum -= Number(candles[index - period].close);
        if (index + 1 >= period) values.push({time: candle.time, value: sum / period});
    });
    return values;
}
