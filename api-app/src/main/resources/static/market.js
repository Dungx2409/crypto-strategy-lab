const marketSocket = {socket: null, connected: false};
const symbolSelect = document.querySelector("#symbol");
const statusBadge = document.querySelector("#connection-status");
const connectionHealth = document.querySelector("#connection-health");
const messageBox = document.querySelector("#market-message");
const realtimeToggle = document.querySelector("#realtime-toggle");

const chartStates = [...document.querySelectorAll(".market-card")].map((card, index) => ({
    index,
    card,
    canvas: card.querySelector("canvas"),
    timeframeSelect: card.querySelector(".chart-timeframe"),
    candles: [],
    destination: null,
    requestVersion: 0
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
    renderChart(index);
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
        renderChart(index);
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
        renderChart(state.index);
        if (state.index === 0) publishMarketSnapshot();
    });
    document.querySelector("#last-market-update").textContent = new Date().toLocaleTimeString();
}

function movingAverage(candles, period) {
    return candles.map((_, index) => index + 1 < period ? null : candles.slice(index + 1 - period, index + 1).reduce((sum, candle) => sum + Number(candle.close), 0) / period);
}

function drawMarketChart(canvas, candles, options = {}) {
    const context = canvas.getContext("2d");
    context.clearRect(0, 0, canvas.width, canvas.height);
    if (!candles.length) return;
    const prices = candles.flatMap(candle => [Number(candle.high), Number(candle.low)]);
    const maximum = Math.max(...prices);
    const minimum = Math.min(...prices);
    const range = maximum - minimum || 1;
    const left = 42, right = 72, top = 18, volumeHeight = 62, bottom = 22;
    const priceBottom = canvas.height - volumeHeight - bottom;
    const plotWidth = canvas.width - left - right;
    const slot = plotWidth / candles.length;
    const y = price => top + ((maximum - price) / range) * (priceBottom - top);
    const x = index => left + index * slot + slot / 2;

    context.font = "11px system-ui";
    context.strokeStyle = "#edf0f5";
    context.fillStyle = "#7b8494";
    context.lineWidth = 1;
    for (let line = 0; line < 5; line++) {
        const value = maximum - range * line / 4;
        const lineY = y(value);
        context.beginPath(); context.moveTo(left, lineY); context.lineTo(canvas.width - right, lineY); context.stroke();
        context.fillText(value.toLocaleString(undefined, {maximumFractionDigits: 2}), canvas.width - right + 7, lineY + 4);
    }

    const maxVolume = Math.max(...candles.map(candle => Number(candle.volume)), 1);
    candles.forEach((candle, index) => {
        const open = Number(candle.open), close = Number(candle.close), rising = close >= open;
        const candleX = x(index), width = Math.max(2, slot * .55);
        context.strokeStyle = rising ? "#16a064" : "#df4052";
        context.fillStyle = context.strokeStyle;
        context.beginPath(); context.moveTo(candleX, y(Number(candle.high))); context.lineTo(candleX, y(Number(candle.low))); context.stroke();
        context.fillRect(candleX - width / 2, Math.min(y(open), y(close)), width, Math.max(2, Math.abs(y(open) - y(close))));
        const volume = Number(candle.volume) / maxVolume * (volumeHeight - 14);
        context.globalAlpha = .42;
        context.fillRect(candleX - width / 2, canvas.height - bottom - volume, width, volume);
        context.globalAlpha = 1;
    });

    const ma20 = movingAverage(candles, 20);
    context.strokeStyle = "#2675dc"; context.lineWidth = 2; context.beginPath();
    ma20.forEach((value, index) => { if (value === null) return; if (index === 19) context.moveTo(x(index), y(value)); else context.lineTo(x(index), y(value)); });
    context.stroke();

    const step = Math.max(1, Math.floor(candles.length / 5));
    candles.forEach((candle, index) => {
        if (index % step !== 0) return;
        context.fillStyle = "#7b8494";
        context.fillText(new Date(candle.openTime).toLocaleString([], {month: "short", day: "numeric", hour: "2-digit", minute: "2-digit"}), Math.max(2, x(index) - 27), canvas.height - 5);
    });

    (options.trades || []).forEach((trade, tradeIndex) => {
        const entry = nearestCandleIndex(candles, trade.entryTime);
        const exit = nearestCandleIndex(candles, trade.exitTime);
        drawTradeMarker(context, x(entry), y(Number(trade.entryPrice)), true, tradeIndex === options.highlight);
        drawTradeMarker(context, x(exit), y(Number(trade.exitPrice)), false, tradeIndex === options.highlight);
    });
}

function nearestCandleIndex(candles, at) {
    const time = new Date(at).getTime();
    return candles.reduce((best, candle, index) => Math.abs(new Date(candle.openTime).getTime() - time) < Math.abs(new Date(candles[best].openTime).getTime() - time) ? index : best, 0);
}

function drawTradeMarker(context, x, y, entry, selected) {
    context.fillStyle = entry ? "#079450" : "#ce3445";
    context.beginPath();
    if (entry) { context.moveTo(x, y - 14); context.lineTo(x - 7, y - 2); context.lineTo(x + 7, y - 2); }
    else { context.moveTo(x, y + 14); context.lineTo(x - 7, y + 2); context.lineTo(x + 7, y + 2); }
    context.closePath(); context.fill();
    if (selected) { context.strokeStyle = "#111827"; context.lineWidth = 2; context.stroke(); }
}

function renderChart(index) {
    const state = chartStates[index];
    drawMarketChart(state.canvas, state.candles);
    const latest = state.candles.at(-1), first = state.candles.at(-2);
    if (!latest) return;
    const close = Number(latest.close), change = first ? (close - Number(first.close)) / Number(first.close) * 100 : 0;
    const maValues = movingAverage(state.candles, 20), ma = maValues.at(-1);
    const signal = ma === null ? "HOLD" : close > ma ? "BUY" : close < ma ? "SELL" : "HOLD";
    state.card.querySelector(".chart-price").textContent = close.toLocaleString(undefined, {maximumFractionDigits: 2});
    const changeNode = state.card.querySelector(".chart-change"); changeNode.textContent = `${change >= 0 ? "+" : ""}${change.toFixed(2)}%`; changeNode.className = `chart-change ${change < 0 ? "negative" : "positive"}`;
    state.card.querySelector(".chart-ma").textContent = ma === null ? "MA(20) calculating" : `MA(20) ${ma.toLocaleString(undefined, {maximumFractionDigits: 2})}`;
    const signalNode = state.card.querySelector(".chart-signal"); signalNode.textContent = signal; signalNode.className = `chart-signal ${signal.toLowerCase()}`;
    state.card.querySelector(".chart-volume").textContent = `Volume ${Number(latest.volume).toLocaleString(undefined, {maximumFractionDigits: 2})}`;
}

function renderBacktestResult(details, highlight = null) {
    const candles = chartStates[0].candles;
    drawMarketChart(document.querySelector("#backtest-chart"), candles, {trades: details.trades || [], highlight});
    const body = document.querySelector("#trade-table-body"); body.replaceChildren();
    if (!details.trades?.length) { const row = body.insertRow(); const cell = row.insertCell(); cell.colSpan = 6; cell.className = "empty"; cell.textContent = "No trades."; return; }
    details.trades.forEach((trade, index) => {
        const row = body.insertRow();
        [index + 1, new Date(trade.entryTime).toLocaleString(), trade.entryPrice, new Date(trade.exitTime).toLocaleString(), trade.exitPrice, trade.pnl].forEach(value => { const cell = row.insertCell(); cell.textContent = value; });
        row.addEventListener("click", () => renderBacktestResult(details, index));
    });
}

function publishMarketSnapshot() {
    const state = chartStates[0];
    document.dispatchEvent(new CustomEvent("crypto-lab:market-data", {detail: {symbol: symbolSelect.value, timeframe: state.timeframeSelect.value, candles: state.candles.map(candle => ({...candle}))}}));
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

window.cryptoLabMarket = {snapshot: () => ({symbol: symbolSelect.value, timeframe: chartStates[0].timeframeSelect.value, candles: chartStates[0].candles.map(candle => ({...candle}))})};
window.cryptoLabBacktest = {render: renderBacktestResult};

symbolSelect.addEventListener("change", () => chartStates.forEach(state => reloadChart(state.index)));
chartStates.forEach(state => state.timeframeSelect.addEventListener("change", () => reloadChart(state.index)));
document.querySelectorAll("[data-primary-timeframe]").forEach(button => button.addEventListener("click", () => {
    document.querySelectorAll("[data-primary-timeframe]").forEach(item => item.classList.toggle("active", item === button));
    chartStates[0].timeframeSelect.value = button.dataset.primaryTimeframe;
    reloadChart(0);
}));
realtimeToggle.addEventListener("change", () => chartStates.forEach(state => realtimeToggle.checked ? subscribeChart(state.index) : unsubscribeChart(state.index)));
chartStates.forEach(state => reloadChart(state.index));
connectMarketSocket();
