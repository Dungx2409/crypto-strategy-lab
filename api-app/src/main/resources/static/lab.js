const labState = { catalog: [], searchRunId: null, searchStartedAt: null, socket: null, connected: false, subscriptions: new Map(), poll: null };
const byId = id => document.getElementById(id);

async function api(url, options) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `${response.status} ${response.statusText}`);
    return body;
}

function setHealth(id, value) { const node = byId(id); node.textContent = value; node.className = `health ${(value || "DOWN").toLowerCase()}`; }
async function refreshSystemStatus() {
    try {
        const status = await api("/api/v1/system/status");
        setHealth("system-market", status.marketData); setHealth("system-news", status.news); setHealth("system-sentiment", status.sentiment); setHealth("system-queue", status.queue);
        byId("system-queue-depth").textContent = status.queueDepth; byId("system-workers").textContent = status.workerConsumers;
        byId("system-running").textContent = status.runningJobs; byId("system-outbox").textContent = status.pendingOutboxEvents;
        byId("status-updated").textContent = `Updated ${new Date().toLocaleTimeString()}`;
    } catch (error) { byId("status-updated").textContent = error.message; }
}

function suggestedValues(name, schema) {
    const value = schema.default;
    if (typeof value !== "number") return String(value ?? "");
    let second = value + (schema.type === "integer" ? 1 : .5);
    if (schema.maximum !== undefined) second = Math.min(second, Number(schema.maximum));
    if (name.toLowerCase().includes("overbought")) second = Math.min(100, value + 5);
    return second === value ? String(value) : `${value}, ${second}`;
}

function renderStrategies() {
    const host = byId("strategy-list"); host.replaceChildren();
    labState.catalog.forEach(plugin => {
        const card = document.createElement("article"); card.className = "strategy-card"; card.dataset.type = plugin.type; card.dataset.version = plugin.version;
        const header = document.createElement("header"); const label = document.createElement("label"); const check = document.createElement("input"); check.type = "checkbox"; check.checked = true; check.className = "strategy-enabled";
        label.append(check, document.createTextNode(` ${plugin.type}`)); const version = document.createElement("span"); version.className = "muted"; version.textContent = plugin.version; header.append(label, version); card.append(header);
        const grid = document.createElement("div"); grid.className = "parameter-grid";
        Object.entries(plugin.parameterSchema).forEach(([name, schema]) => { const field = document.createElement("label"); field.textContent = name; const input = document.createElement("input"); input.dataset.parameter = name; input.dataset.parameterType = schema.type; input.value = suggestedValues(name, schema); field.append(input); grid.append(field); });
        card.append(grid); host.append(card);
    });
}

async function loadCapabilities() {
    const [catalog, capabilities] = await Promise.all([api("/api/v1/strategies"), api("/api/v1/search-runs/capabilities")]);
    labState.catalog = catalog; renderStrategies();
    const generator = byId("generator"); generator.replaceChildren(); capabilities.availableGenerators.forEach(type => { const option = document.createElement("option"); option.value = type; option.textContent = type.toUpperCase(); option.selected = type === capabilities.defaultGenerator; generator.append(option); });
}

function selectedSearchConfiguration() {
    const strategyTypes = [], strategyVersions = {}, parameterSpace = {}, weights = {};
    document.querySelectorAll(".strategy-card").forEach(card => {
        if (!card.querySelector(".strategy-enabled").checked) return;
        const type = card.dataset.type; strategyTypes.push(type); strategyVersions[type] = card.dataset.version; weights[type] = 1; parameterSpace[type] = {};
        card.querySelectorAll("[data-parameter]").forEach(input => { parameterSpace[type][input.dataset.parameter] = input.value.split(",").map(raw => raw.trim()).filter(Boolean).map(raw => input.dataset.parameterType === "integer" ? Number.parseInt(raw,10) : Number(raw)); });
    });
    if (!strategyTypes.length) throw new Error("Select at least one strategy.");
    const policy = byId("combination-policy").value;
    return { strategyTypes, strategyVersions, parameterSpace, combinationPolicy: { type: policy, version: "1.0", weights: policy === "WEIGHTED" ? weights : {}, threshold: policy === "WEIGHTED" ? Number(byId("policy-threshold").value) : 0 } };
}

async function materializeDataset() {
    const snapshot = window.cryptoLabMarket.snapshot();
    if (snapshot.candles.length < 2) throw new Error("At least two backend candles are required before starting search.");
    return api("/api/v1/datasets", { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify({ symbol: snapshot.symbol, timeframe: snapshot.timeframe, datasetVersion: `dashboard-${snapshot.timeframe}-v1`, candles: snapshot.candles }) });
}

async function startSearch() {
    byId("start-search").disabled = true; byId("search-message").textContent = "Materializing immutable market dataset…";
    try {
        const dataset = await materializeDataset(); const config = selectedSearchConfiguration();
        const request = { symbol: dataset.symbol, timeframe: dataset.timeframe, from: dataset.from, to: dataset.to, datasetVersion: dataset.datasetVersion, datasetChecksum: dataset.checksum, ...config, randomSeed: Number(byId("random-seed").value), stopConditions: { maxCandidates: Number(byId("max-candidates").value), maxDuration: null, noImprovementIterations: null }, batchSize: Number(byId("batch-size").value), executionConfig: null };
        const run = await api(`/api/v1/search-runs?generator=${encodeURIComponent(byId("generator").value)}`, { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify(request) });
        labState.searchRunId = run.searchRunId; labState.searchStartedAt = Date.now(); byId("cancel-search").disabled = false; byId("search-message").textContent = `Search ${run.searchRunId}`;
        subscribeProofTopics(run.searchRunId); renderSearch(run); beginPolling();
    } catch (error) { byId("search-message").textContent = error.message; byId("start-search").disabled = false; }
}

async function cancelSearch() { if (!labState.searchRunId) return; try { renderSearch(await api(`/api/v1/search-runs/${labState.searchRunId}/cancel`, {method:"POST"})); } catch (error) { byId("search-message").textContent = error.message; } }
function renderSearch(run) {
    const terminal = ["COMPLETED","FAILED","CANCELLED"].includes(run.status); const badge = byId("search-status"); badge.textContent = `${run.status} · ${run.generatorType}`; badge.className = `status ${run.status === "FAILED" ? "status-offline" : terminal ? "status-online" : "status-degraded"}`;
    byId("generated-count").textContent = run.generatedCandidates; byId("pending-count").textContent = run.pendingDispatchJobs; byId("queued-count").textContent = run.queuedJobs; byId("running-count").textContent = run.runningJobs; byId("completed-count").textContent = run.completedJobs; byId("failed-count").textContent = run.failedJobs; byId("best-score").textContent = run.bestScore ?? "—";
    const max = Number(byId("max-candidates").value) || 1; byId("search-progress-bar").style.width = `${Math.min(100, (run.generatedCandidates / max) * 100)}%`;
    const started = run.startedAt ? new Date(run.startedAt).getTime() : labState.searchStartedAt; const ended = run.endedAt ? new Date(run.endedAt).getTime() : Date.now(); byId("elapsed-time").textContent = started ? `${Math.max(0, Math.round((ended-started)/1000))}s` : "0s";
    byId("cancel-search").disabled = terminal; byId("start-search").disabled = !terminal;
    if (run.failureMessage) byId("search-message").textContent = run.failureMessage;
}

function beginPolling() { clearInterval(labState.poll); labState.poll = setInterval(async () => { if (!labState.searchRunId) return; try { const run = await api(`/api/v1/search-runs/${labState.searchRunId}`); renderSearch(run); await loadLeaderboard(); } catch (error) { byId("search-message").textContent = error.message; } }, 2000); }
async function loadLeaderboard() {
    if (!labState.searchRunId) return; const data = await api(`/api/v1/leaderboard?searchRunId=${labState.searchRunId}&limit=50`); const body = byId("leaderboard-body"); body.replaceChildren();
    if (!data.items.length) { const row = body.insertRow(); const cell = row.insertCell(); cell.colSpan = 6; cell.className = "empty"; cell.textContent = "No completed experiments yet."; return; }
    data.items.forEach(item => { const row = body.insertRow(); row.dataset.experimentId = item.experimentId; [item.rank,item.strategySummary,`${item.returnPct}%`,`${item.maxDrawdownPct}%`,item.totalTrades,item.score].forEach(value => { const cell = row.insertCell(); cell.textContent = value; }); row.addEventListener("click", () => loadExperiment(item.experimentId)); });
    if (!document.querySelector("[data-selected-experiment]") && data.items[0]) loadExperiment(data.items[0].experimentId);
}

function provenanceItem(label, value) { const item = document.createElement("article"); item.className = "provenance-item"; const name = document.createElement("span"); name.textContent = label; const data = document.createElement("strong"); data.textContent = value ?? "—"; item.append(name,data); return item; }
async function loadExperiment(experimentId) {
    document.querySelectorAll("#leaderboard-body tr").forEach(row => { if (row.dataset.experimentId === experimentId) row.dataset.selectedExperiment = "true"; else delete row.dataset.selectedExperiment; });
    byId("experiment-message").textContent = "Loading immutable result…";
    try {
        const [details, provenance] = await Promise.all([api(`/api/v1/experiments/${experimentId}`), api(`/api/v1/experiments/${experimentId}/provenance`)]); byId("experiment-rank").textContent = details.rank ? `TOP #${details.rank}` : details.status; byId("experiment-rank").className = "status status-online"; byId("experiment-message").textContent = `${details.strategies.map(s => `${s.type}@${s.version}`).join(" + ")} · ${details.dataset.symbol} ${details.dataset.timeframe}`;
        const grid = byId("provenance-grid"); grid.replaceChildren(provenanceItem("Experiment",details.experimentId),provenanceItem("Candidate hash",details.candidateHash),provenanceItem("Dataset checksum",details.dataset.checksum),provenanceItem("Dataset range",`${details.dataset.from} → ${details.dataset.to}`),provenanceItem("Generator",`${details.generator.type}@${details.generator.version}`),provenanceItem("Evaluator",details.evaluatorVersion),provenanceItem("Engine",`${details.executionConfig.engineVersion} · ${details.executionConfig.fillPolicy}`),provenanceItem("Code / build",`${details.codeCommit} / ${details.buildVersion}`),provenanceItem("Return",details.metrics ? `${details.metrics.totalReturnPct}%` : "—"),provenanceItem("MDD",details.metrics ? `${details.metrics.maxDrawdownPct}%` : "—"),provenanceItem("Trades",details.metrics?.totalTrades),provenanceItem("Score",details.metrics?.score));
        renderArtifacts("signals", details.signals, signal => `${signal.at} · ${signal.strategyType}@${signal.strategyVersion} · ${signal.type} (${signal.strength}) · ${signal.reason}`); renderArtifacts("trades", details.trades, trade => `${trade.entryTime} @ ${trade.entryPrice} → ${trade.exitTime} @ ${trade.exitPrice} · PnL ${trade.pnl}`); byId("provenance-json").textContent = JSON.stringify(provenance,null,2);
    } catch (error) { byId("experiment-message").textContent = error.message; }
}
function renderArtifacts(id, items, describe) { const host = byId(id); host.replaceChildren(); if (!items?.length) { host.className = "artifact-list empty"; host.textContent = `No ${id}.`; return; } host.className = "artifact-list"; items.forEach(item => { const row = document.createElement("div"); row.className = "artifact-row"; row.textContent = describe(item); host.append(row); }); }

function connectProofSocket() { const protocol = location.protocol === "https:" ? "wss" : "ws"; labState.socket = new WebSocket(`${protocol}://${location.host}/ws`); labState.socket.addEventListener("open", () => labState.socket.send(`CONNECT\naccept-version:1.2\nhost:${location.host}\nheart-beat:10000,10000\n\n\u0000`)); labState.socket.addEventListener("message", event => event.data.split("\u0000").filter(Boolean).forEach(handleProofFrame)); labState.socket.addEventListener("close", () => { labState.connected=false; setTimeout(connectProofSocket,2000); }); }
function handleProofFrame(frame) { if (frame.startsWith("CONNECTED")) { labState.connected=true; labState.subscriptions.forEach((handler,destination) => sendProofSubscription(destination)); return; } if (!frame.startsWith("MESSAGE")) return; const split=frame.indexOf("\n\n"); const destination=(frame.match(/\ndestination:([^\n]+)/)||[])[1]; if (split>=0 && destination && labState.subscriptions.has(destination)) labState.subscriptions.get(destination)(JSON.parse(frame.slice(split+2))); }
function sendProofSubscription(destination) { if (!labState.connected) return; const id=`proof-${[...labState.subscriptions.keys()].indexOf(destination)}`; labState.socket.send(`SUBSCRIBE\nid:${id}\ndestination:${destination}\nack:auto\n\n\u0000`); }
function subscribeProofTopics(searchRunId) { const search=`/topic/search/${searchRunId}`, leaderboard=`/topic/leaderboard/${searchRunId}`; labState.subscriptions.set(search, renderSearch); labState.subscriptions.set(leaderboard, loadLeaderboard); sendProofSubscription(search); sendProofSubscription(leaderboard); }

byId("start-search").addEventListener("click", startSearch); byId("cancel-search").addEventListener("click", cancelSearch); byId("combination-policy").addEventListener("change", event => byId("policy-threshold").disabled = event.target.value !== "WEIGHTED"); byId("policy-threshold").disabled = true;
loadCapabilities().catch(error => byId("search-message").textContent = error.message); refreshSystemStatus(); setInterval(refreshSystemStatus,5000); connectProofSocket();
