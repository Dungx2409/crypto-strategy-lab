const marketState = {
    socket: null,
    connected: false,
    subscribedDestination: null,
    candles: []
};

const symbolSelect = document.querySelector("#symbol");
const timeframeSelect = document.querySelector("#timeframe");
const statusBadge = document.querySelector("#connection-status");
const messageBox = document.querySelector("#market-message");
const canvas = document.querySelector("#candle-chart");
const context = canvas.getContext("2d");

function sendStomp(frame) {
    if (marketState.socket?.readyState === WebSocket.OPEN) {
        marketState.socket.send(`${frame}\u0000`);
    }
}

function connectMarketSocket() {
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    marketState.socket = new WebSocket(`${protocol}://${location.host}/ws`);
    marketState.socket.addEventListener("open", () => {
        sendStomp(`CONNECT\naccept-version:1.2\nhost:${location.host}\nheart-beat:10000,10000\n\n`);
    });
    marketState.socket.addEventListener("message", event => handleStompFrames(event.data));
    marketState.socket.addEventListener("close", () => {
        marketState.connected = false;
        marketState.subscribedDestination = null;
        setConnectionStatus(false);
        window.setTimeout(connectMarketSocket, 2000);
    });
}

function handleStompFrames(raw) {
    raw.split("\u0000").filter(Boolean).forEach(frame => {
        if (frame.startsWith("CONNECTED")) {
            marketState.connected = true;
            setConnectionStatus(true);
            subscribeMarketStream();
            return;
        }
        if (frame.startsWith("MESSAGE")) {
            const separator = frame.indexOf("\n\n");
            if (separator >= 0) {
                acceptRealtimeCandle(JSON.parse(frame.substring(separator + 2)));
            }
        }
    });
}

function subscribeMarketStream() {
    if (!marketState.connected) return;
    const destination = `/topic/market/${symbolSelect.value}/${timeframeSelect.value}`;
    if (marketState.subscribedDestination === destination) return;
    sendStomp(`SUBSCRIBE\nid:market-chart\ndestination:${destination}\nack:auto\n\n`);
    marketState.subscribedDestination = destination;
}

function unsubscribeMarketStream() {
    if (marketState.connected && marketState.subscribedDestination) {
        sendStomp("UNSUBSCRIBE\nid:market-chart\n\n");
    }
    marketState.subscribedDestination = null;
}

async function loadSelectedMarketData() {
    const params = new URLSearchParams({
        symbol: symbolSelect.value,
        timeframe: timeframeSelect.value,
        limit: "120"
    });
    messageBox.textContent = "Loading historical candles…";
    const response = await fetch(`/api/v1/market/candles?${params}`);
    const body = await response.json();
    if (!response.ok) {
        throw new Error(body.message || "Market data request failed");
    }
    marketState.candles = body.candles;
    messageBox.textContent = body.degraded ? "Showing cached data while Binance is unavailable." : "";
    renderCandles();
    publishMarketSnapshot();
}

async function reloadMarketSlice() {
    unsubscribeMarketStream();
    try {
        await loadSelectedMarketData();
    } catch (error) {
        marketState.candles = [];
        messageBox.textContent = error.message;
        renderCandles();
    }
    subscribeMarketStream();
}

function acceptRealtimeCandle(event) {
    if (event.type !== "CANDLE_CLOSED"
            || event.symbol !== symbolSelect.value
            || event.timeframe !== timeframeSelect.value) return;
    const index = marketState.candles.findIndex(candle => candle.openTime === event.openTime);
    const candle = {
        openTime: event.openTime,
        open: event.open,
        high: event.high,
        low: event.low,
        close: event.close,
        volume: event.volume
    };
    if (index >= 0) marketState.candles[index] = candle;
    else marketState.candles.push(candle);
    marketState.candles = marketState.candles.slice(-120);
    renderCandles();
    publishMarketSnapshot();
}

function publishMarketSnapshot() {
    document.dispatchEvent(new CustomEvent("crypto-lab:market-data", {
        detail: {
            symbol: symbolSelect.value,
            timeframe: timeframeSelect.value,
            candles: marketState.candles.map(candle => ({...candle}))
        }
    }));
}

window.cryptoLabMarket = {
    snapshot: () => ({
        symbol: symbolSelect.value,
        timeframe: timeframeSelect.value,
        candles: marketState.candles.map(candle => ({...candle}))
    })
};

function renderCandles() {
    context.clearRect(0, 0, canvas.width, canvas.height);
    if (!marketState.candles.length) return;
    const values = marketState.candles.flatMap(candle => [Number(candle.high), Number(candle.low)]);
    const maximum = Math.max(...values);
    const minimum = Math.min(...values);
    const range = maximum - minimum || 1;
    const top = 30;
    const height = canvas.height - 60;
    const slot = canvas.width / marketState.candles.length;
    const y = price => top + ((maximum - price) / range) * height;

    marketState.candles.forEach((candle, index) => {
        const open = Number(candle.open);
        const close = Number(candle.close);
        const x = index * slot + slot / 2;
        const rising = close >= open;
        context.strokeStyle = rising ? "#34d399" : "#f87171";
        context.fillStyle = context.strokeStyle;
        context.beginPath();
        context.moveTo(x, y(Number(candle.high)));
        context.lineTo(x, y(Number(candle.low)));
        context.stroke();
        const bodyTop = Math.min(y(open), y(close));
        const bodyHeight = Math.max(2, Math.abs(y(open) - y(close)));
        context.fillRect(x - Math.max(1, slot * .28), bodyTop, Math.max(2, slot * .56), bodyHeight);
    });
}

function setConnectionStatus(connected) {
    statusBadge.textContent = connected ? "CONNECTED" : "OFFLINE";
    statusBadge.className = `status ${connected ? "status-online" : "status-offline"}`;
}

symbolSelect.addEventListener("change", reloadMarketSlice);
timeframeSelect.addEventListener("change", reloadMarketSlice);
reloadMarketSlice();
connectMarketSocket();
