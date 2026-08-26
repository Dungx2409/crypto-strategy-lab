import {simpleMovingAverage} from "/chart-math.js";

const marketSocket = {socket: null, connected: false};
const symbolSelect = document.querySelector("#symbol");
const statusBadge = document.querySelector("#connection-status");
const connectionHealth = document.querySelector("#connection-health");
const messageBox = document.querySelector("#market-message");
const realtimeToggle = document.querySelector("#realtime-toggle");

function chartOptions(container) {
    return {
        width: container.clientWidth || 720,
        height: container.classList.contains("backtest-chart") ? 520 : 360,
        layout: {background: {type: "solid", color: "#ffffff"}, textColor: "#64748b"},
        grid: {vertLines: {color: "#edf0f5"}, horzLines: {color: "#edf0f5"}},
        timeScale: {timeVisible: true, secondsVisible: false, borderColor: "#e2e8f0"},
        rightPriceScale: {borderColor: "#e2e8f0"},
        crosshair: {mode: LightweightCharts.CrosshairMode.Normal}
    };
}

function observeSize(container, chart) {
    new ResizeObserver(entries => {
        const width = Math.floor(entries[0].contentRect.width);
        if (width > 0) chart.applyOptions({width});
    }).observe(container);
}

function createChartState(card, index) {
    const container = card.querySelector(".trading-chart");
    const chart = LightweightCharts.createChart(container, chartOptions(container));
    const candleSeries = chart.addSeries(LightweightCharts.CandlestickSeries, {
        upColor: "#16a064", downColor: "#df4052", borderVisible: false,
        wickUpColor: "#16a064", wickDownColor: "#df4052"
    });
    const volumeSeries = chart.addSeries(LightweightCharts.HistogramSeries, {
        priceFormat: {type: "volume"}, priceScaleId: "volume"
    });
    chart.priceScale("volume").applyOptions({scaleMargins: {top: .8, bottom: 0}});
    observeSize(container, chart);
    return {index, card, chart, candleSeries, volumeSeries,
        timeframeSelect: card.querySelector(".chart-timeframe"), candles: [], destination: null,
        requestVersion: 0};
}

const chartStates = [...document.querySelectorAll(".market-card")].map(createChartState);
const backtestContainer = document.querySelector("#backtest-chart");
const backtestChart = LightweightCharts.createChart(backtestContainer, chartOptions(backtestContainer));
const backtestCandles = backtestChart.addSeries(LightweightCharts.CandlestickSeries, {
    upColor: "#16a064", downColor: "#df4052", borderVisible: false,
    wickUpColor: "#16a064", wickDownColor: "#df4052"
});
const backtestOverlays = [];
let backtestMarkers = null;
observeSize(backtestContainer, backtestChart);

const unixTime = value => Math.floor(new Date(value).getTime() / 1000);
const normalizedCandles = candles => candles.map(candle => ({
    time: unixTime(candle.openTime), open: Number(candle.open), high: Number(candle.high),
    low: Number(candle.low), close: Number(candle.close), volume: Number(candle.volume)
}));

function sendStomp(frame) {
    if (marketSocket.socket?.readyState === WebSocket.OPEN) marketSocket.socket.send(`${frame}\u0000`);
}

function connectMarketSocket() {
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    marketSocket.socket = new WebSocket(`${protocol}://${location.host}/ws`);
    marketSocket.socket.addEventListener("open", () => sendStomp(`CONNECT\naccept-version:1.2\nhost:${location.host}\nheart-beat:10000,10000\n\n`));
    marketSocket.socket.addEventListener("message", event => handleStompFrames(event.data));
    marketSocket.socket.addEventListener("close", () => {
        marketSocket.connected = false;
        chartStates.forEach(state => state.destination = null);
        setConnectionStatus(false);
        window.setTimeout(connectMarketSocket, 2000);
    });
}

function handleStompFrames(raw) {
    raw.split("\u0000").filter(Boolean).forEach(frame => {
        if (frame.startsWith("CONNECTED")) {
            marketSocket.connected = true;
            setConnectionStatus(true);
            chartStates.forEach(state => subscribeChart(state.index));
            return;
        }
        if (!frame.startsWith("MESSAGE")) return;
        const separator = frame.indexOf("\n\n");
        if (separator >= 0) acceptRealtimeCandle(JSON.parse(frame.substring(separator + 2)));
    });
}

function subscribeChart(index) {
    const state = chartStates[index];
    if (!marketSocket.connected || !realtimeToggle.checked) return;
    const destination = `/topic/market/${symbolSelect.value}/${state.timeframeSelect.value}`;
    if (state.destination === destination) return;
    sendStomp(`SUBSCRIBE\nid:market-chart-${index}\ndestination:${destination}\nack:auto\n\n`);
    state.destination = destination;
    updateStreamCount();
}

function unsubscribeChart(index) {
    const state = chartStates[index];
    if (marketSocket.connected && state.destination) sendStomp(`UNSUBSCRIBE\nid:market-chart-${index}\n\n`);
    state.destination = null;
    updateStreamCount();
}

async function loadChart(index) {
    const state = chartStates[index];
    const requestVersion = ++state.requestVersion;
    state.card.querySelector(".chart-update").textContent = "Loading history";
    const params = new URLSearchParams({symbol: symbolSelect.value, timeframe: state.timeframeSelect.value, limit: "160"});
    const response = await fetch(`/api/v1/market/candles?${params}`);
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "Market data request failed");
    if (requestVersion !== state.requestVersion) return;
    state.candles = body.candles;
    state.card.querySelector(".chart-update").textContent = body.degraded ? "Cached history" : "History loaded";
    renderChart(index, true);
    if (index === 0) publishMarketSnapshot();
}

async function reloadChart(index) {
    unsubscribeChart(index);
    try {
        await loadChart(index);
        messageBox.textContent = "";
    } catch (error) {
        chartStates[index].candles = [];
        chartStates[index].card.querySelector(".chart-update").textContent = "Unavailable";
        messageBox.textContent = error.message;
        renderChart(index, true);
    }
    subscribeChart(index);
}

function acceptRealtimeCandle(event) {
    if (event.type !== "CANDLE_UPDATE" || !realtimeToggle.checked || event.symbol !== symbolSelect.value) return;
    chartStates.filter(state => state.timeframeSelect.value === event.timeframe).forEach(state => {
        const candle = {openTime: event.openTime, open: event.open, high: event.high, low: event.low, close: event.close, volume: event.volume};
        const index = state.candles.findIndex(item => item.openTime === event.openTime);
        if (index >= 0) state.candles[index] = candle;
        else state.candles.push(candle);
        state.candles.sort((left, right) => new Date(left.openTime) - new Date(right.openTime));
        state.candles = state.candles.slice(-160);
        state.card.querySelector(".chart-update").textContent = event.closed ? "Closed candle" : "Updating current candle";
        renderChart(state.index, false);
        if (state.index === 0) publishMarketSnapshot();
    });
    document.querySelector("#last-market-update").textContent = new Date().toLocaleTimeString();
}

function renderChart(index, fitContent) {
    const state = chartStates[index];
    const data = normalizedCandles(state.candles);
    state.candleSeries.setData(data);
    state.volumeSeries.setData(data.map(candle => ({time: candle.time, value: candle.volume,
        color: candle.close >= candle.open ? "rgba(22,160,100,.35)" : "rgba(223,64,82,.35)"})));
    if (fitContent) state.chart.timeScale().fitContent();
    const latest = state.candles.at(-1), previous = state.candles.at(-2);
    if (!latest) return;
    const close = Number(latest.close);
    const change = previous ? (close - Number(previous.close)) / Number(previous.close) * 100 : 0;
    state.card.querySelector(".chart-price").textContent = close.toLocaleString(undefined, {maximumFractionDigits: 2});
    const changeNode = state.card.querySelector(".chart-change");
    changeNode.textContent = `${change >= 0 ? "+" : ""}${change.toFixed(2)}%`;
    changeNode.className = `chart-change ${change < 0 ? "negative" : "positive"}`;
    state.card.querySelector(".chart-volume").textContent = `Volume ${Number(latest.volume).toLocaleString(undefined, {maximumFractionDigits: 2})}`;
}

function addLine(data, color, title) {
    const series = backtestChart.addSeries(LightweightCharts.LineSeries, {color, lineWidth: 2, title});
    series.setData(data);
    backtestOverlays.push(series);
}

function rolling(candles, period, calculate) {
    const values = [];
    candles.forEach((candle, index) => {
        if (index + 1 >= period) values.push({time: candle.time, ...calculate(candles.slice(index + 1 - period, index + 1))});
    });
    return values;
}

function renderPluginOverlays(candles, strategies, catalog) {
    while (backtestOverlays.length) backtestChart.removeSeries(backtestOverlays.pop());
    strategies.forEach(strategy => {
        const plugin = catalog.find(item => item.type === strategy.type && item.version === strategy.version);
        (plugin?.overlays || []).forEach(overlay => {
            const config = overlay.configuration;
            const period = Number(strategy.parameters[config.periodParameter]);
            if (!Number.isInteger(period) || period < 1) return;
            if (overlay.kind === "SMA") {
                addLine(simpleMovingAverage(candles, period), config.color, `${strategy.type} ${overlay.id}`);
            } else if (overlay.kind === "BOLLINGER") {
                const multiplier = Number(strategy.parameters[config.deviationParameter]);
                const bands = rolling(candles, period, window => {
                    const mean = window.reduce((sum, item) => sum + item.close, 0) / period;
                    const deviation = Math.sqrt(window.reduce((sum, item) => sum + (item.close - mean) ** 2, 0) / period);
                    return {middle: mean, upper: mean + multiplier * deviation, lower: mean - multiplier * deviation};
                });
                addLine(bands.map(item => ({time: item.time, value: item.upper})), config.color, "Bollinger upper");
                addLine(bands.map(item => ({time: item.time, value: item.middle})), "#94a3b8", "Bollinger middle");
                addLine(bands.map(item => ({time: item.time, value: item.lower})), config.color, "Bollinger lower");
            } else if (overlay.kind === "PRICE_CHANNEL") {
                const channel = rolling(candles, period, window => ({
                    upper: Math.max(...window.map(item => item.high)), lower: Math.min(...window.map(item => item.low))
                }));
                addLine(channel.map(item => ({time: item.time, value: item.upper})), config.color, "Resistance");
                addLine(channel.map(item => ({time: item.time, value: item.lower})), config.color, "Support");
            }
        });
    });
}

function setTradeMarkers(trades, highlight) {
    const markers = trades.flatMap((trade, index) => [
        {time: unixTime(trade.entryTime), position: trade.direction === "SHORT" ? "aboveBar" : "belowBar",
            color: trade.direction === "SHORT" ? "#ce3445" : "#079450",
            shape: trade.direction === "SHORT" ? "arrowDown" : "arrowUp", text: `Entry ${index + 1}`, size: index === highlight ? 2 : 1},
        {time: unixTime(trade.exitTime), position: trade.direction === "SHORT" ? "belowBar" : "aboveBar",
            color: "#2563eb", shape: trade.direction === "SHORT" ? "arrowUp" : "arrowDown",
            text: `Exit ${index + 1}`, size: index === highlight ? 2 : 1}
    ]).sort((left, right) => left.time - right.time);
    if (backtestMarkers?.setMarkers) backtestMarkers.setMarkers(markers);
    else if (LightweightCharts.createSeriesMarkers) backtestMarkers = LightweightCharts.createSeriesMarkers(backtestCandles, markers);
    else backtestCandles.setMarkers(markers);
}

function renderBacktestResult(details, dataset, catalog, highlight = null) {
    const candles = normalizedCandles(dataset.candles || []);
    backtestCandles.setData(candles);
    renderPluginOverlays(candles, details.strategies || [], catalog || []);
    setTradeMarkers(details.trades || [], highlight);
    backtestChart.timeScale().fitContent();
    const body = document.querySelector("#trade-table-body");
    body.replaceChildren();
    if (!details.trades?.length) {
        const row = body.insertRow(); const cell = row.insertCell();
        cell.colSpan = 8; cell.className = "empty"; cell.textContent = "No trades."; return;
    }
    details.trades.forEach((trade, index) => {
        const row = body.insertRow();
        [index + 1, trade.direction || "LONG", new Date(trade.entryTime).toLocaleString(), trade.entryPrice,
            new Date(trade.exitTime).toLocaleString(), trade.exitPrice, trade.exitReason || "SIGNAL", trade.pnl]
            .forEach(value => { const cell = row.insertCell(); cell.textContent = value; });
        row.addEventListener("click", () => renderBacktestResult(details, dataset, catalog, index));
    });
}

function publishMarketSnapshot() {
    const state = chartStates[0];
    document.dispatchEvent(new CustomEvent("crypto-lab:market-data", {detail: {symbol: symbolSelect.value,
        timeframe: state.timeframeSelect.value, candles: state.candles.map(candle => ({...candle}))}}));
}

function setConnectionStatus(connected) {
    statusBadge.textContent = connected ? "RECEIVING DATA" : "OFFLINE";
    statusBadge.className = `status ${connected ? "status-online" : "status-offline"}`;
    connectionHealth.textContent = connected ? "UP" : "DOWN";
    connectionHealth.className = `health ${connected ? "up" : "down"}`;
    document.querySelector("#data-source-status").classList.toggle("offline", !connected);
}

function updateStreamCount() {
    document.querySelector("#stream-count").textContent = `${chartStates.filter(state => state.destination).length} charts`;
}

window.cryptoLabMarket = {snapshot: () => ({symbol: symbolSelect.value,
    timeframe: chartStates[0].timeframeSelect.value, candles: chartStates[0].candles.map(candle => ({...candle}))})};
window.cryptoLabBacktest = {render: renderBacktestResult};

symbolSelect.addEventListener("change", () => chartStates.forEach(state => {
    state.card.querySelector("strong").firstChild.textContent = `${symbolSelect.value} · `;
    reloadChart(state.index);
}));
chartStates.forEach(state => state.timeframeSelect.addEventListener("change", () => reloadChart(state.index)));
document.querySelectorAll("[data-primary-timeframe]").forEach(button => button.addEventListener("click", () => {
    document.querySelectorAll("[data-primary-timeframe]").forEach(item => item.classList.toggle("active", item === button));
    chartStates[0].timeframeSelect.value = button.dataset.primaryTimeframe;
    reloadChart(0);
}));
realtimeToggle.addEventListener("change", () => chartStates.forEach(state =>
    realtimeToggle.checked ? subscribeChart(state.index) : unsubscribeChart(state.index)));
chartStates.forEach(state => reloadChart(state.index));
connectMarketSocket();
