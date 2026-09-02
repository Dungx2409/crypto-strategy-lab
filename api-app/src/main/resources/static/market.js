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
    requestVersion: 0,
    visibleCount: 80,
    offset: 0,
    hoverIndex: null,
    loadingEarlier: false
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
    state.offset = 0;
    state.card.querySelector(".chart-update").textContent = body.degraded ? "Cached history" : "History loaded";
    renderChart(index);
    if (index === 0) publishMarketSnapshot();
}

const timeframeMilliseconds = {"1m":60000,"5m":300000,"15m":900000,"30m":1800000,"1h":3600000,"2h":7200000,"4h":14400000,"1d":86400000};

async function loadEarlier(index) {
    const state = chartStates[index];
    if (state.loadingEarlier || !state.candles.length) return;
    const remaining = 20000 - state.candles.length;
    if (remaining <= 0) {
        state.card.querySelector(".chart-update").textContent = "20,000-candle history limit";
        return;
    }
    const amount = Math.min(160, remaining);
    state.loadingEarlier = true;
    state.card.querySelector(".chart-update").textContent = "Loading earlier candles";
    try {
        const to = new Date(state.candles[0].openTime);
        const from = new Date(to.getTime() - timeframeMilliseconds[state.timeframeSelect.value] * amount);
        const params = new URLSearchParams({symbol:symbolSelect.value,timeframe:state.timeframeSelect.value,limit:String(amount),from:from.toISOString(),to:to.toISOString()});
        const response = await fetch(`/api/v1/market/candles?${params}`);
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || "Earlier market data request failed");
        const merged = new Map([...body.candles, ...state.candles].map(candle => [candle.openTime,candle]));
        const added = merged.size - state.candles.length;
        state.candles = [...merged.values()].sort((left,right)=>new Date(left.openTime)-new Date(right.openTime));
        state.offset += Math.max(0, added);
        state.card.querySelector(".chart-update").textContent = added ? `${added} earlier candles loaded` : "No earlier candles";
        renderChart(index);
    } catch (error) {
        state.card.querySelector(".chart-update").textContent = error.message;
    } finally {
        state.loadingEarlier = false;
    }
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
        if (state.candles.length > 20000) state.candles = state.candles.slice(-20000);
        state.card.querySelector(".chart-update").textContent = event.closed ? "Closed candle" : "Updating current candle";
        renderChart(state.index);
        if (state.index === 0) publishMarketSnapshot();
    });
    document.querySelector("#last-market-update").textContent = new Date().toLocaleTimeString();
}

function movingAverage(candles, period) {
    return candles.map((_, index) => index + 1 < period ? null : candles.slice(index + 1 - period, index + 1).reduce((sum, candle) => sum + Number(candle.close), 0) / period);
}

function visibleCandles(state) {
    const end = Math.max(0,state.candles.length-state.offset);
    return state.candles.slice(Math.max(0,end-state.visibleCount),end);
}

function rollingBands(candles, period=20, deviations=2) {
    return candles.map((_,index)=>{
        if(index+1<period)return null;
        const values=candles.slice(index+1-period,index+1).map(candle=>Number(candle.close));
        const mean=values.reduce((sum,value)=>sum+value,0)/period;
        const deviation=Math.sqrt(values.reduce((sum,value)=>sum+(value-mean)**2,0)/period);
        return {upper:mean+deviations*deviation,lower:mean-deviations*deviation};
    });
}

function relativeStrengthIndex(candles, period=14) {
    return candles.map((_,index)=>{
        if(index<period)return null;
        let gains=0,losses=0;
        for(let cursor=index-period+1;cursor<=index;cursor++){
            const change=Number(candles[cursor].close)-Number(candles[cursor-1].close);
            if(change>0)gains+=change;else losses-=change;
        }
        if(!gains&&!losses)return 50;
        if(!losses)return 100;
        if(!gains)return 0;
        return 100-(100/(1+gains/losses));
    });
}

function drawMarketChart(canvas, candles, options = {}) {
    const context = canvas.getContext("2d");
    context.clearRect(0, 0, canvas.width, canvas.height);
    if (!candles.length) return;
    const strategyTypes = new Set(options.strategyTypes || ["MOVING_AVERAGE"]);
    const bands = strategyTypes.has("BOLLINGER_BANDS") ? rollingBands(candles) : [];
    const prices = candles.flatMap(candle => [Number(candle.high), Number(candle.low)]);
    bands.filter(Boolean).forEach(band=>prices.push(band.upper,band.lower));
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
        context.fillText(formatPrice(value), canvas.width - right + 7, lineY + 4);
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

    if (strategyTypes.has("BOLLINGER_BANDS")) {
        ["upper","lower"].forEach(key=>{context.strokeStyle="#8b5cf6";context.lineWidth=1;context.beginPath();let started=false;bands.forEach((band,index)=>{if(!band)return;started?context.lineTo(x(index),y(band[key])):context.moveTo(x(index),y(band[key]));started=true;});context.stroke();});
    }
    if (strategyTypes.has("SUPPORT_RESISTANCE")) {
        const recent=candles.slice(-Math.min(20,candles.length));
        const support=Math.min(...recent.map(candle=>Number(candle.low)));
        const resistance=Math.max(...recent.map(candle=>Number(candle.high)));
        [[support,"#16a064"],[resistance,"#df4052"]].forEach(([value,color])=>{context.strokeStyle=color;context.setLineDash([5,4]);context.beginPath();context.moveTo(left,y(value));context.lineTo(canvas.width-right,y(value));context.stroke();context.setLineDash([]);});
    }
    if (strategyTypes.has("RSI")) {
        const rsi=relativeStrengthIndex(candles),indicatorTop=priceBottom+8,indicatorBottom=canvas.height-bottom;
        const rsiY=value=>indicatorBottom-(value/100)*(indicatorBottom-indicatorTop);
        [30,70].forEach(value=>{context.strokeStyle="#cbd5e1";context.setLineDash([3,3]);context.beginPath();context.moveTo(left,rsiY(value));context.lineTo(canvas.width-right,rsiY(value));context.stroke();context.setLineDash([]);context.fillStyle="#64748b";context.fillText(String(value),canvas.width-right+8,rsiY(value)+3);});
        context.strokeStyle="#d97706";context.lineWidth=1.5;context.beginPath();let started=false;rsi.forEach((value,index)=>{if(value===null)return;started?context.lineTo(x(index),rsiY(value)):context.moveTo(x(index),rsiY(value));started=true;});context.stroke();
    }

    const step = Math.max(1, Math.floor(candles.length / 5));
    candles.forEach((candle, index) => {
        if (index % step !== 0) return;
        context.fillStyle = "#7b8494";
        context.fillText(new Date(candle.openTime).toLocaleString([], {month: "short", day: "numeric", hour: "2-digit", minute: "2-digit"}), Math.max(2, x(index) - 27), canvas.height - 5);
    });

    const visibleFrom=new Date(candles[0].openTime).getTime();
    const lastOpen=new Date(candles.at(-1).openTime).getTime();
    const candleWidth=candles.length>1?lastOpen-new Date(candles.at(-2).openTime).getTime():1;
    const visibleTo=lastOpen+candleWidth;
    (options.trades || []).forEach((trade, tradeIndex) => {
        const entryTime=new Date(trade.entryTime).getTime(),exitTime=new Date(trade.exitTime).getTime();
        if(entryTime>=visibleFrom&&entryTime<=visibleTo){const entry=nearestCandleIndex(candles,trade.entryTime);drawTradeMarker(context,x(entry),y(Number(trade.entryPrice)),true,trade.direction||"LONG",tradeIndex===options.highlight);}
        if(exitTime>=visibleFrom&&exitTime<=visibleTo){const exit=nearestCandleIndex(candles,trade.exitTime);drawTradeMarker(context,x(exit),y(Number(trade.exitPrice)),false,trade.direction||"LONG",tradeIndex===options.highlight);}
    });
    (options.signals || []).filter(signal=>signal.strategyType==="COMPOSITE"&&signal.type!=="HOLD"&&new Date(signal.at).getTime()>=visibleFrom&&new Date(signal.at).getTime()<=visibleTo).forEach(signal=>{
        const candleIndex=nearestCandleIndex(candles,signal.at);
        drawSignalMarker(context,x(candleIndex),y(Number(candles[candleIndex].close)),signal.type);
    });
    if (options.hoverIndex !== null && options.hoverIndex !== undefined && candles[options.hoverIndex]) {
        const candle=candles[options.hoverIndex], hoverX=x(options.hoverIndex);
        context.strokeStyle="#64748b";context.setLineDash([3,3]);context.beginPath();context.moveTo(hoverX,top);context.lineTo(hoverX,canvas.height-bottom);context.stroke();context.setLineDash([]);
        context.fillStyle="rgba(15,23,42,.88)";context.fillRect(left+8,top+8,270,42);context.fillStyle="#fff";context.font="11px system-ui";
        context.fillText(`${new Date(candle.openTime).toLocaleString()}  O ${formatPrice(candle.open)}  H ${formatPrice(candle.high)}`,left+16,top+25);
        context.fillText(`L ${formatPrice(candle.low)}  C ${formatPrice(candle.close)}  V ${formatPrice(candle.volume)}`,left+16,top+42);
    }
}

function formatPrice(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number.toFixed(2) : "—";
}

function drawSignalMarker(context,x,y,type){context.fillStyle=type==="BUY"?"#16a064":"#df4052";context.font="bold 10px system-ui";context.fillText(type==="BUY"?"▲ BUY":"▼ SELL",x-18,y+(type==="BUY"?18:-10));}

function nearestCandleIndex(candles, at) {
    const time = new Date(at).getTime();
    return candles.reduce((best, candle, index) => Math.abs(new Date(candle.openTime).getTime() - time) < Math.abs(new Date(candles[best].openTime).getTime() - time) ? index : best, 0);
}

function drawTradeMarker(context, x, y, entry, direction, selected) {
    const pointsUp = entry ? direction === "LONG" : direction === "SHORT";
    context.fillStyle = entry ? (direction === "LONG" ? "#079450" : "#ce3445") : "#2563eb";
    context.beginPath();
    if (pointsUp) { context.moveTo(x, y - 14); context.lineTo(x - 7, y - 2); context.lineTo(x + 7, y - 2); }
    else { context.moveTo(x, y + 14); context.lineTo(x - 7, y + 2); context.lineTo(x + 7, y + 2); }
    context.closePath(); context.fill();
    if (selected) { context.strokeStyle = "#111827"; context.lineWidth = 2; context.stroke(); }
}

function renderChart(index) {
    const state = chartStates[index];
    const candles=visibleCandles(state);
    drawMarketChart(state.canvas, candles, {hoverIndex:state.hoverIndex});
    const latest = state.candles.at(-1), first = state.candles.at(-2);
    if (!latest) return;
    const close = Number(latest.close), change = first ? (close - Number(first.close)) / Number(first.close) * 100 : 0;
    const maValues = movingAverage(state.candles, 20), ma = maValues.at(-1);
    const signal = ma === null ? "HOLD" : close > ma ? "BUY" : close < ma ? "SELL" : "HOLD";
    state.card.querySelector(".chart-price").textContent = formatPrice(close);
    const changeNode = state.card.querySelector(".chart-change"); changeNode.textContent = `${change >= 0 ? "+" : ""}${change.toFixed(2)}%`; changeNode.className = `chart-change ${change < 0 ? "negative" : "positive"}`;
    state.card.querySelector(".chart-ma").textContent = ma === null ? "MA(20) calculating" : `MA(20) ${formatPrice(ma)}`;
    const signalNode = state.card.querySelector(".chart-signal"); signalNode.textContent = signal; signalNode.className = `chart-signal ${signal.toLowerCase()}`;
    state.card.querySelector(".chart-volume").textContent = `Volume ${formatPrice(latest.volume)}`;
}

function renderBacktestResult(details, highlight = null) {
    let candles = details.candles || [];
    if (highlight !== null && details.trades?.[highlight] && candles.length>240) {
        const center=nearestCandleIndex(candles,details.trades[highlight].entryTime);
        candles=candles.slice(Math.max(0,center-100),Math.min(candles.length,center+140));
    } else if(candles.length>500) candles=candles.slice(-500);
    const strategyTypes=(details.strategies||[]).flatMap(strategy=>strategy.type==="RULE"&&(strategy.parameters?.buyMetric==="RSI"||strategy.parameters?.sellMetric==="RSI")?["RULE","RSI"]:[strategy.type]);
    drawMarketChart(document.querySelector("#backtest-chart"), candles, {trades: details.trades || [],signals:details.signals||[],strategyTypes,highlight});
    const body = document.querySelector("#trade-table-body"); body.replaceChildren();
    if (!details.trades?.length) { const row = body.insertRow(); const cell = row.insertCell(); cell.colSpan = 8; cell.className = "empty"; cell.textContent = "No trades."; return; }
    details.trades.forEach((trade, index) => {
        const row = body.insertRow();
        [index + 1, trade.direction || "LONG", new Date(trade.entryTime).toLocaleString(), trade.entryPrice, new Date(trade.exitTime).toLocaleString(), trade.exitPrice, trade.exitReason || "SIGNAL", trade.pnl].forEach(value => { const cell = row.insertCell(); cell.textContent = value; });
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

symbolSelect.addEventListener("change", () => chartStates.forEach(state => {
    state.card.querySelector("strong").firstChild.textContent = `${symbolSelect.value} · `;
    reloadChart(state.index);
}));

function syncSymbolPicker(value) {
    const picker = document.querySelector("[data-symbol-picker]");
    if (!picker) return;
    const trigger = picker.querySelector(".symbol-picker-trigger");
    const triggerIcon = trigger.querySelector(".coin-icon");
    const triggerText = trigger.querySelector(".symbol-picker-text");
    const options = [...picker.querySelectorAll('[role="option"]')];
    const selected = options.find(option => option.dataset.value === value) || options[0];
    if (!selected) return;
    options.forEach(option => option.setAttribute("aria-selected", option === selected ? "true" : "false"));
    triggerIcon.src = selected.dataset.icon;
    triggerText.textContent = selected.dataset.value;
}

function setSymbolPickerOpen(open) {
    const picker = document.querySelector("[data-symbol-picker]");
    if (!picker) return;
    const trigger = picker.querySelector(".symbol-picker-trigger");
    const menu = picker.querySelector(".symbol-picker-menu");
    trigger.setAttribute("aria-expanded", open ? "true" : "false");
    menu.hidden = !open;
}

function initSymbolPicker() {
    const picker = document.querySelector("[data-symbol-picker]");
    if (!picker || !symbolSelect) return;
    const trigger = picker.querySelector(".symbol-picker-trigger");
    const menu = picker.querySelector(".symbol-picker-menu");
    syncSymbolPicker(symbolSelect.value);
    trigger.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        setSymbolPickerOpen(menu.hidden);
    });
    menu.querySelectorAll('[role="option"]').forEach(option => {
        option.addEventListener("click", event => {
            event.preventDefault();
            event.stopPropagation();
            const value = option.dataset.value;
            if (!value || symbolSelect.value === value) {
                setSymbolPickerOpen(false);
                return;
            }
            symbolSelect.value = value;
            syncSymbolPicker(value);
            setSymbolPickerOpen(false);
            symbolSelect.dispatchEvent(new Event("change", {bubbles: true}));
        });
    });
    document.addEventListener("click", event => {
        if (!picker.contains(event.target)) setSymbolPickerOpen(false);
    });
    document.addEventListener("keydown", event => {
        if (event.key === "Escape") setSymbolPickerOpen(false);
    });
}

initSymbolPicker();
chartStates.forEach(state => state.timeframeSelect.addEventListener("change", () => reloadChart(state.index)));
chartStates.forEach(state=>{
    let dragStart=null;
    state.canvas.addEventListener("wheel",event=>{event.preventDefault();if(event.shiftKey){state.offset=Math.max(0,Math.min(state.candles.length-state.visibleCount,state.offset+Math.sign(event.deltaY)*10));if(state.offset>=state.candles.length-state.visibleCount-5)loadEarlier(state.index);}else{state.visibleCount=Math.max(30,Math.min(state.candles.length,state.visibleCount+Math.sign(event.deltaY)*10));}renderChart(state.index);},{passive:false});
    state.canvas.addEventListener("pointerdown",event=>{dragStart={x:event.clientX,offset:state.offset};state.canvas.setPointerCapture(event.pointerId);});
    state.canvas.addEventListener("pointermove",event=>{const rect=state.canvas.getBoundingClientRect(),candles=visibleCandles(state),scale=state.canvas.width/rect.width,canvasX=(event.clientX-rect.left)*scale;state.hoverIndex=Math.max(0,Math.min(candles.length-1,Math.floor((canvasX-42)/(state.canvas.width-114)*candles.length)));if(dragStart){const moved=Math.round((event.clientX-dragStart.x)*scale/(state.canvas.width-114)*state.visibleCount);state.offset=Math.max(0,Math.min(Math.max(0,state.candles.length-state.visibleCount),dragStart.offset+moved));if(state.offset>=state.candles.length-state.visibleCount-5)loadEarlier(state.index);}renderChart(state.index);});
    state.canvas.addEventListener("pointerup",()=>dragStart=null);
    state.canvas.addEventListener("pointerleave",()=>{dragStart=null;state.hoverIndex=null;renderChart(state.index);});
    state.canvas.addEventListener("dblclick",()=>{state.offset=0;state.visibleCount=Math.min(80,state.candles.length);renderChart(state.index);});
});
document.querySelectorAll("[data-primary-timeframe]").forEach(button => button.addEventListener("click", () => {
    document.querySelectorAll("[data-primary-timeframe]").forEach(item => item.classList.toggle("active", item === button));
    chartStates[0].timeframeSelect.value = button.dataset.primaryTimeframe;
    reloadChart(0);
}));
realtimeToggle.addEventListener("change", () => chartStates.forEach(state => realtimeToggle.checked ? subscribeChart(state.index) : unsubscribeChart(state.index)));
chartStates.forEach(state => reloadChart(state.index));
connectMarketSocket();
