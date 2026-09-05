const labState = { catalog: [], userStrategies: [], capabilities: null, searchRunId: null, currentSearchRun: null, searchStartedAt: null, stopConditions: null, discoveryHistory: [], startingSearch: false, socket: null, connected: false, subscriptions: new Map(), poll: null };
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

const strategyIcons = {
    MA: "〰",
    RSI: "∿",
    BB: "▤",
    SR: "⌂",
    MACD: "↗",
    RULE: "☰",
    NEWS_SENTIMENT: "◉",
    SENTIMENT: "◉"
};

function strategyIcon(type) {
    const key = String(type || "").toUpperCase();
    return strategyIcons[key] || "⌘";
}

function savedStrategyPlugins() {
    return labState.userStrategies.flatMap(strategy => {
        const definitions = strategy.document?.strategies || [];
        if (definitions.length !== 1) return [];
        const definition = definitions[0];
        const label = definition.displayLabel || acronym(strategy.document.name);
        const fullName = strategy.document.name || label;
        const catalogPlugin = labState.catalog.find(plugin => plugin.type === definition.type && plugin.version === definition.version);
        const allowedParameters = new Set(Object.keys(catalogPlugin?.parameterSchema || {}));
        const fixedParameters = catalogPlugin
            ? Object.fromEntries(Object.entries(definition.parameters || {})
                .filter(([name]) => allowedParameters.has(name)))
            : definition.parameters || {};
        return [{
            type: definition.type,
            version: definition.version,
            parameterSchema: {},
            fixedParameters,
            displayName: strategyDisplayName(fullName, label),
            displayVersion: `saved v${strategy.version}`,
            displayLabel: label,
            savedStrategyId: strategy.id
        }];
    });
}

function strategyDisplayName(name, label) {
    const cleanName = String(name || "").trim();
    const cleanLabel = String(label || "").trim();
    if (!cleanLabel) return cleanName || "AI strategy";
    if (!cleanName || cleanName.toUpperCase() === cleanLabel.toUpperCase()) return cleanLabel;
    if (cleanName.toUpperCase().endsWith(`(${cleanLabel.toUpperCase()})`)) return cleanName;
    return `${cleanName} (${cleanLabel})`;
}

function acronym(value) {
    const words = String(value || "").toUpperCase().match(/[A-Z0-9]+/g) || [];
    const label = words.map(word => word[0]).join("").slice(0, 16);
    return label.length >= 2 ? label : "AI";
}

function discoveryCatalog() {
    return [...labState.catalog, ...savedStrategyPlugins()];
}

function renderStrategies() {
    const host = byId("strategy-list"); host.replaceChildren();
    discoveryCatalog().forEach(plugin => {
        const card = document.createElement("article"); card.className = "strategy-card"; card.dataset.type = plugin.type; card.dataset.version = plugin.version;
        if (plugin.fixedParameters) card.dataset.fixedParameters = JSON.stringify(plugin.fixedParameters);
        if (plugin.savedStrategyId) card.dataset.savedStrategyId = plugin.savedStrategyId;
        if (plugin.displayLabel) card.dataset.displayLabel = plugin.displayLabel;
        const header = document.createElement("header"); const label = document.createElement("label"); const check = document.createElement("input"); check.type = "checkbox"; check.checked = !plugin.savedStrategyId; check.className = "strategy-enabled";
        const icon = document.createElement("span"); icon.className = "strategy-type-icon"; icon.setAttribute("aria-hidden", "true"); icon.textContent = strategyIcon(plugin.type);
        label.append(check, icon, document.createTextNode(` ${plugin.displayName || plugin.type}`)); const version = document.createElement("span"); version.className = "muted"; version.textContent = plugin.displayVersion || plugin.version; header.append(label, version); card.append(header);
        const grid = document.createElement("div"); grid.className = "parameter-grid";
        Object.entries(plugin.parameterSchema).forEach(([name, schema]) => { const field = document.createElement("label"); field.textContent = name; const input = document.createElement("input"); input.dataset.parameter = name; input.dataset.parameterType = schema.type; input.value = suggestedValues(name, schema); field.append(input); grid.append(field); });
        Object.entries(plugin.fixedParameters || {}).forEach(([name, value]) => {
            const field = document.createElement("label");
            field.textContent = `${name} (fixed)`;
            const input = document.createElement(String(value).length > 80 ? "textarea" : "input");
            input.value = typeof value === "object" ? JSON.stringify(value) : String(value);
            input.disabled = true;
            field.append(input);
            grid.append(field);
        });
        const weightField = document.createElement("label");
        weightField.className = "weight-field";
        const weightCaption = document.createElement("span");
        weightCaption.className = "weight-caption";
        weightCaption.textContent = "Voting weight";
        const weightValue = document.createElement("strong");
        weightValue.className = "weight-value";
        weightValue.textContent = "1.0";
        const weight = document.createElement("input");
        weight.type = "range";
        weight.min = "0.1";
        weight.max = "1";
        weight.step = "0.1";
        weight.value = "1";
        weight.className = "strategy-weight";
        weight.setAttribute("aria-valuetext", "1.0");
        const syncWeightValue = () => {
            const formatted = Number(weight.value).toFixed(1);
            weightValue.textContent = formatted;
            weight.setAttribute("aria-valuetext", formatted);
        };
        weight.addEventListener("input", syncWeightValue);
        syncWeightValue();
        weightField.append(weightCaption, weightValue, weight);
        check.addEventListener("change", renderSelectedStrategyChips);
        const advanced = document.createElement("details");
        advanced.className = "strategy-advanced";
        const summary = document.createElement("summary");
        summary.textContent = "Fine tune";
        advanced.append(summary, grid, weightField);
        card.append(advanced); host.append(card);
    });
    renderSelectedStrategyChips();
}

function renderSelectedStrategyChips() {
    const host = byId("selected-strategy-chips");
    host.replaceChildren();
    document.querySelectorAll(".strategy-card").forEach(card => {
        if (!card.querySelector(".strategy-enabled").checked) return;
        const chip = document.createElement("span");
        chip.className = "chip";
        const icon = document.createElement("span");
        icon.className = "icon";
        icon.setAttribute("aria-hidden", "true");
        icon.textContent = strategyIcon(card.dataset.type);
        chip.append(icon, document.createTextNode(card.dataset.savedStrategyId ? card.querySelector("label").textContent.trim() : card.dataset.type));
        host.append(chip);
    });
}

async function loadCapabilities() {
    const [catalog, capabilities] = await Promise.all([api("/api/v1/strategies"), api("/api/v1/search-runs/capabilities")]);
    labState.catalog = catalog; labState.capabilities = capabilities; labState.userStrategies = window.cryptoLabUserStrategies || []; renderStrategies();
    window.cryptoLabAccountFeatures?.refreshManualStrategyOptions?.();
    byId("execution-version").value = `${capabilities.engineVersion} · ${capabilities.fillPolicy}`;
    const generator = byId("generator"); generator.replaceChildren(); capabilities.availableGenerators.forEach(type => { const option = document.createElement("option"); option.value = type; option.textContent = type.toUpperCase(); option.selected = type === capabilities.defaultGenerator; generator.append(option); });
}

function refreshDiscoveryUserStrategies(strategies) {
    labState.userStrategies = strategies || [];
    renderStrategies();
}

function discoveryRunLabel(run) {
    const created = run.createdAt ? new Date(run.createdAt).toLocaleString() : "unknown time";
    const best = run.bestScore ?? "—";
    const strategies = run.strategySummary ? ` · ${run.strategySummary}` : "";
    return `${created} · ${run.status} · ${run.generatorType}${strategies} · best ${best}`;
}

async function loadDiscoveryHistory(selectedId = labState.searchRunId) {
    const runs = await api("/api/v1/search-runs?limit=25");
    labState.discoveryHistory = runs;
    const select = byId("discovery-history");
    select.replaceChildren();
    if (!runs.length) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = "No discovery runs yet";
        select.append(option);
        return;
    }
    runs.forEach(run => {
        const option = document.createElement("option");
        option.value = run.searchRunId;
        option.textContent = discoveryRunLabel(run);
        option.selected = run.searchRunId === selectedId;
        select.append(option);
    });
    if (selectedId && runs.some(run => run.searchRunId === selectedId)) {
        select.value = selectedId;
    }
}

function selectedSearchConfiguration() {
    const strategyTypes = [], strategyVersions = {}, strategyLabels = {}, parameterSpace = {}, weights = {};
    document.querySelectorAll(".strategy-card").forEach(card => {
        if (!card.querySelector(".strategy-enabled").checked) return;
        const type = card.dataset.type; strategyTypes.push(type); strategyVersions[type] = card.dataset.version; weights[type] = Number(card.querySelector(".strategy-weight").value); parameterSpace[type] = {};
        if (card.dataset.displayLabel) strategyLabels[type] = card.dataset.displayLabel;
        if (card.dataset.fixedParameters) {
            Object.entries(JSON.parse(card.dataset.fixedParameters)).forEach(([name, value]) => {
                parameterSpace[type][name] = [value];
            });
        }
        card.querySelectorAll("[data-parameter]").forEach(input => {
            parameterSpace[type][input.dataset.parameter] = input.value.split(",")
                .map(raw => raw.trim()).filter(Boolean)
                .map(raw => input.dataset.parameterType === "integer"
                    ? Number.parseInt(raw, 10)
                    : input.dataset.parameterType === "number" ? Number(raw) : raw);
        });
    });
    if (!strategyTypes.length) throw new Error("Select at least one strategy.");
    if (new Set(strategyTypes).size !== strategyTypes.length) {
        throw new Error("Discovery can use only one strategy of each type at a time. Select one saved AI strategy or one built-in strategy for the same type.");
    }
    const policy = byId("combination-policy").value;
    return { strategyTypes, strategyVersions, strategyLabels, parameterSpace, combinationPolicy: { type: policy, version: "1.0", weights: policy === "WEIGHTED" ? weights : {}, threshold: policy === "WEIGHTED" ? Number(byId("policy-threshold").value) : 0 } };
}

async function materializeDataset() {
    const snapshot = window.cryptoLabMarket.snapshot();
    if (snapshot.candles.length < 2) throw new Error("At least two backend candles are required before starting search.");
    const sentimentObservations = window.cryptoLabNews?.snapshot?.() ?? [];
    return api("/api/v1/datasets", { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify({ symbol: snapshot.symbol, timeframe: snapshot.timeframe, datasetVersion: `dashboard-${snapshot.timeframe}-v2`, candles: snapshot.candles, sentimentObservations }) });
}

function selectedStopConditions() {
    const positiveInteger = (id, label) => {
        const node = byId(id);
        if (!node) return null;
        const raw = node.value.trim();
        if (raw === "") return null;
        const value = Number(raw);
        if (!Number.isInteger(value) || value <= 0) throw new Error(`${label} must be a positive integer.`);
        return value;
    };
    const maxCandidates = positiveInteger("max-candidates", "Max candidates");
    const maxDurationSeconds = positiveInteger("max-duration-seconds", "Max time");
    const noImprovementIterations = positiveInteger("no-improvement-iterations", "No improvement iterations");
    if (maxCandidates === null && maxDurationSeconds === null && noImprovementIterations === null) {
        throw new Error("Configure at least one automatic stop condition.");
    }
    return {
        request: {
            maxCandidates,
            maxDuration: maxDurationSeconds === null ? null : `PT${maxDurationSeconds}S`,
            noImprovementIterations
        },
        maxCandidates,
        maxDurationSeconds
    };
}

async function startSearch() {
    if (labState.startingSearch) return;
    labState.startingSearch = true;
    byId("start-search").disabled = true;
    byId("search-message").textContent = "Materializing immutable market dataset…";
    byId("search-status").textContent = "STARTING";
    byId("search-status").className = "status status-degraded";
    try {
        const dataset = await materializeDataset();
        byId("search-message").textContent = "Submitting discovery run…";
        const config = selectedSearchConfiguration();
        const stops = selectedStopConditions();
        const optionalNumber = id => byId(id).value === "" ? null : Number(byId(id).value);
        const executionConfig = { initialCapital: Number(byId("initial-capital").value), feeRate: Number(byId("fee-rate").value), allowShort: byId("allow-short").checked, fillPolicy: labState.capabilities.fillPolicy, engineVersion: labState.capabilities.engineVersion, positionSizePct: Number(byId("position-size-pct").value), stopLossPct: optionalNumber("stop-loss-pct"), takeProfitPct: optionalNumber("take-profit-pct"), trailingStopPct: optionalNumber("trailing-stop-pct") };
        const generator = byId("generator").value;
        const request = { symbol: dataset.symbol, timeframe: dataset.timeframe, from: dataset.from, to: dataset.to, datasetVersion: dataset.datasetVersion, datasetChecksum: dataset.checksum, ...config, randomSeed: Number(byId("random-seed").value), stopConditions: stops.request, batchSize: Number(byId("batch-size").value), executionConfig };
        const run = await api(`/api/v1/search-runs?generator=${encodeURIComponent(generator)}`, { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify(request) });
        labState.searchRunId = run.searchRunId; labState.searchStartedAt = Date.now(); labState.stopConditions = stops; labState.startingSearch = false; byId("cancel-search").disabled = false; byId("start-search").disabled = false;
        byId("search-message").textContent = generator === "genetic"
            ? `Search ${run.searchRunId} · genetic generations wait for worker fitness between rounds`
            : `Search ${run.searchRunId}`;
        subscribeProofTopics(run.searchRunId); renderSearch(run); await loadDiscoveryHistory(run.searchRunId); await loadLeaderboard(); await loadAllTimeLeaderboard(); beginPolling();
    } catch (error) { labState.startingSearch = false; byId("search-message").textContent = error.message; byId("start-search").disabled = false; byId("search-status").textContent = "FAILED"; byId("search-status").className = "status status-offline"; }
}

async function cancelSearch() { if (!labState.searchRunId) return; try { renderSearch(await api(`/api/v1/search-runs/${labState.searchRunId}/cancel`, {method:"POST"})); } catch (error) { byId("search-message").textContent = error.message; } }
function renderSearch(run) {
    labState.currentSearchRun = run;
    const terminal = ["COMPLETED","FAILED","CANCELLED"].includes(run.status); const badge = byId("search-status"); badge.textContent = `${run.status} · ${run.generatorType}`; badge.className = `status ${run.status === "FAILED" ? "status-offline" : terminal ? "status-online" : "status-degraded"}`;
    byId("generated-count").textContent = run.generatedCandidates; byId("pending-count").textContent = run.pendingDispatchJobs; byId("queued-count").textContent = run.queuedJobs; byId("running-count").textContent = run.runningJobs; byId("completed-count").textContent = run.completedJobs; byId("failed-count").textContent = run.failedJobs; byId("best-score").textContent = run.bestScore ?? "—";
    const started = run.startedAt ? new Date(run.startedAt).getTime() : labState.searchStartedAt; const ended = run.endedAt ? new Date(run.endedAt).getTime() : Date.now(); byId("elapsed-time").textContent = started ? `${Math.max(0, Math.round((ended-started)/1000))}s` : "0s";
    const progress = byId("search-progress-bar"), maxCandidates = labState.stopConditions?.maxCandidates, maxDurationSeconds = labState.stopConditions?.maxDurationSeconds;
    const percent = maxCandidates ? run.generatedCandidates / maxCandidates * 100 : maxDurationSeconds && started ? (ended-started) / 1000 / maxDurationSeconds * 100 : null;
    progress.classList.toggle("indeterminate", percent === null && !terminal); progress.style.width = percent === null ? (terminal ? "100%" : "35%") : `${Math.min(100, percent)}%`;
    byId("cancel-search").disabled = terminal; byId("start-search").disabled = labState.startingSearch;
    if (run.failureMessage) byId("search-message").textContent = run.failureMessage;
}

function beginPolling() { clearInterval(labState.poll); labState.poll = setInterval(async () => { if (!labState.searchRunId) return; try { const run = await api(`/api/v1/search-runs/${labState.searchRunId}`); renderSearch(run); await loadLeaderboard(); } catch (error) { byId("search-message").textContent = error.message; } }, 2000); }
async function loadLeaderboard() {
    if (!labState.searchRunId) return; const query = new URLSearchParams({searchRunId: labState.searchRunId, limit: "50", sort: byId("leaderboard-sort").value, direction: byId("leaderboard-direction").value}); const data = await api(`/api/v1/leaderboard?${query}`); const body = byId("leaderboard-body"); body.replaceChildren();
    if (!data.items.length) { const row = body.insertRow(); const cell = row.insertCell(); cell.colSpan = 7; cell.className = "empty"; cell.textContent = "No completed experiments yet."; return; }
    data.items.forEach(item => { const row = body.insertRow(); row.dataset.experimentId = item.experimentId; if (labState.selectedExperimentId === item.experimentId) row.dataset.selectedExperiment = "true"; [item.rank,item.strategySummary,`${item.returnPct}%`,`${item.winRatePct ?? "-"}%`,`${item.maxDrawdownPct}%`,item.totalTrades,item.score].forEach(value => { const cell = row.insertCell(); cell.textContent = value; }); row.addEventListener("click", () => { loadExperiment(item.experimentId); if (typeof showView === "function") showView("backtest"); }); });
    if (!labState.selectedExperimentId && data.items[0]) { loadExperiment(data.items[0].experimentId); } else if (labState.selectedExperimentId && !document.querySelector("[data-selected-experiment]")) { const exists = data.items.some(i => i.experimentId === labState.selectedExperimentId); if (!exists && data.items[0]) loadExperiment(data.items[0].experimentId); }
}

async function loadExperiment(experimentId) {
    labState.selectedExperimentId = experimentId;
    document.querySelectorAll("#leaderboard-body tr").forEach(row => { if (row.dataset.experimentId === experimentId) row.dataset.selectedExperiment = "true"; else delete row.dataset.selectedExperiment; });
    byId("experiment-message").textContent = "Loading immutable result…";
    try {
        const [details, provenance, dataset] = await Promise.all([api(`/api/v1/experiments/${experimentId}`), api(`/api/v1/experiments/${experimentId}/provenance`),api(`/api/v1/experiments/${experimentId}/candles`)]); details.candles=dataset.candles; byId("experiment-rank").textContent = details.rank ? `TOP #${details.rank}` : details.status; byId("experiment-rank").className = "status status-online"; byId("experiment-message").textContent = `${experimentRunText(details)} · ${generatorText(details.generator)} · ${strategyShortText(details.strategies)} · ${details.dataset.symbol} ${details.dataset.timeframe} · ${formatDateTime(details.startedAt)}`;
        byId("manual-timeframe").value = details.dataset.timeframe;
        byId("manual-symbol").value = details.dataset.symbol;
        byId("metric-win-rate").textContent = details.metrics ? `${details.metrics.winRatePct ?? "-"}%` : "-";
        byId("metric-return").textContent = details.metrics ? `${details.metrics.totalReturnPct}%` : "-";
        byId("metric-drawdown").textContent = details.metrics ? `${details.metrics.maxDrawdownPct}%` : "-";
        byId("metric-trades").textContent = details.metrics?.totalTrades ?? "-";
        const grid = byId("provenance-grid"); grid.replaceChildren(provenanceItem("Run",experimentRunText(details)),provenanceItem("Run time",`${formatDateTime(details.startedAt)} to ${formatDateTime(details.completedAt)}`),provenanceItem("Search method",generatorText(details.generator)),provenanceItem("Strategies used",strategyConfigText(details.strategies)),provenanceItem("Experiment",details.experimentId),provenanceItem("Candidate hash",details.candidateHash),provenanceItem("Dataset checksum",details.dataset.checksum),provenanceItem("Dataset range",`${details.dataset.from} to ${details.dataset.to}`),provenanceItem("Evaluator",details.evaluatorVersion),provenanceItem("Engine",`${details.executionConfig.engineVersion} · ${details.executionConfig.fillPolicy}`),provenanceItem("Code / build",`${details.codeCommit} / ${details.buildVersion}`),provenanceItem("Return",details.metrics ? `${details.metrics.totalReturnPct}%` : "-"),provenanceItem("Win rate",details.metrics ? `${details.metrics.winRatePct ?? "-"}%` : "-"),provenanceItem("MDD",details.metrics ? `${details.metrics.maxDrawdownPct}%` : "-"),provenanceItem("Trades",details.metrics?.totalTrades),provenanceItem("Score",details.metrics?.score));
        renderArtifacts("signals", details.signals, signal => `${signal.at} · ${signal.strategyType}@${signal.strategyVersion} · ${signal.type} (${signal.strength}) · ${signal.reason}`); renderArtifacts("trades", details.trades, trade => `${trade.direction || "LONG"} · ${trade.entryTime} @ ${trade.entryPrice} to ${trade.exitTime} @ ${trade.exitPrice} · ${trade.exitReason || "SIGNAL"} · PnL ${trade.pnl}`); byId("provenance-json").textContent = JSON.stringify(provenance,null,2);
        window.cryptoLabCurrentExperiment = details;
        window.cryptoLabBacktest.render(details);
    } catch (error) { byId("experiment-message").textContent = error.message; }
}
function renderArtifacts(id, items, describe) { const host = byId(id); host.replaceChildren(); if (!items?.length) { host.className = "artifact-list empty"; host.textContent = `No ${id}.`; return; } host.className = "artifact-list"; items.forEach(item => { const row = document.createElement("div"); row.className = "artifact-row"; row.textContent = describe(item); host.append(row); }); }

function renderLeaderboardRows(bodyId, items, emptyText, showRun = false) {
    const body = byId(bodyId);
    body.replaceChildren();
    if (!items.length) {
        const row = body.insertRow();
        const cell = row.insertCell();
        cell.colSpan = showRun ? 8 : 7;
        cell.className = "empty";
        cell.textContent = emptyText;
        return;
    }
    items.forEach(item => {
        const row = body.insertRow();
        row.dataset.experimentId = item.experimentId;
        if (labState.selectedExperimentId === item.experimentId) row.dataset.selectedExperiment = "true";
        const values = showRun
            ? [item.strategySummary, `${item.returnPct}%`, `${item.winRatePct ?? "-"}%`, `${item.maxDrawdownPct}%`, item.totalTrades, item.score]
            : [item.rank, item.strategySummary, `${item.returnPct}%`, `${item.winRatePct ?? "-"}%`, `${item.maxDrawdownPct}%`, item.totalTrades, item.score];
        if (showRun) {
            const rankCell = row.insertCell();
            rankCell.textContent = item.rank;
            appendLeaderboardRunCell(row, item);
        }
        values
            .forEach(value => {
                const cell = row.insertCell();
                cell.textContent = value;
            });
        row.addEventListener("click", () => {
            loadExperiment(item.experimentId);
            if (typeof showView === "function") showView("backtest");
        });
    });
}

async function loadLeaderboard() {
    if (!labState.searchRunId) return;
    const query = new URLSearchParams({
        searchRunId: labState.searchRunId,
        limit: "50",
        sort: byId("leaderboard-sort").value,
        direction: byId("leaderboard-direction").value
    });
    const data = await api(`/api/v1/leaderboard?${query}`);
    const run = labState.discoveryHistory.find(item => item.searchRunId === labState.searchRunId)
        || (labState.currentSearchRun?.searchRunId === labState.searchRunId ? labState.currentSearchRun : null);
    byId("leaderboard-run-label").textContent = run
        ? `Showing ${discoveryRunLabel(run)}`
        : `Showing discovery ${labState.searchRunId}`;
    renderLeaderboardRows("leaderboard-body", data.items, "No completed experiments yet.");
    if (!labState.selectedExperimentId && data.items[0]) {
        loadExperiment(data.items[0].experimentId);
    } else if (labState.selectedExperimentId && !document.querySelector("[data-selected-experiment]")) {
        const exists = data.items.some(item => item.experimentId === labState.selectedExperimentId);
        if (!exists && data.items[0]) loadExperiment(data.items[0].experimentId);
    }
}

async function loadAllTimeLeaderboard() {
    const query = new URLSearchParams({
        limit: "20",
        sort: byId("leaderboard-sort").value,
        direction: byId("leaderboard-direction").value
    });
    const data = await api(`/api/v1/leaderboard/all-time?${query}`);
    renderLeaderboardRows("all-time-leaderboard-body", data.items, "No completed experiments yet.", true);
}

async function selectDiscoveryRun(searchRunId) {
    if (!searchRunId) return;
    labState.searchRunId = searchRunId;
    const run = await api(`/api/v1/search-runs/${searchRunId}`);
    labState.currentSearchRun = run;
    labState.searchStartedAt = run.startedAt ? new Date(run.startedAt).getTime() : null;
    labState.stopConditions = { ...run.stopConditions };
    renderSearch(run);
    await loadDiscoveryHistory(searchRunId);
    await loadLeaderboard();
}

function connectProofSocket() { const protocol = location.protocol === "https:" ? "wss" : "ws"; labState.socket = new WebSocket(`${protocol}://${location.host}/ws`); labState.socket.addEventListener("open", () => labState.socket.send(`CONNECT\naccept-version:1.2\nhost:${location.host}\nheart-beat:10000,10000\n\n\u0000`)); labState.socket.addEventListener("message", event => event.data.split("\u0000").filter(Boolean).forEach(handleProofFrame)); labState.socket.addEventListener("close", () => { labState.connected=false; setTimeout(connectProofSocket,2000); }); }
function handleProofFrame(frame) { if (frame.startsWith("CONNECTED")) { labState.connected=true; labState.subscriptions.forEach((handler,destination) => sendProofSubscription(destination)); return; } if (!frame.startsWith("MESSAGE")) return; const split=frame.indexOf("\n\n"); const destination=(frame.match(/\ndestination:([^\n]+)/)||[])[1]; if (split>=0 && destination && labState.subscriptions.has(destination)) labState.subscriptions.get(destination)(JSON.parse(frame.slice(split+2))); }
function sendProofSubscription(destination) { if (!labState.connected) return; const id=`proof-${[...labState.subscriptions.keys()].indexOf(destination)}`; labState.socket.send(`SUBSCRIBE\nid:${id}\ndestination:${destination}\nack:auto\n\n\u0000`); }
function subscribeProofTopics(searchRunId) {
    const search = `/topic/search/${searchRunId}`, leaderboard = `/topic/leaderboard/${searchRunId}`;
    labState.subscriptions.set(search, run => {
        if (run.searchRunId === labState.searchRunId) renderSearch(run);
    });
    labState.subscriptions.set(leaderboard, loadLeaderboard);
    sendProofSubscription(search);
    sendProofSubscription(leaderboard);
}

byId("start-search").addEventListener("click", startSearch); byId("cancel-search").addEventListener("click", cancelSearch);
["leaderboard-sort","leaderboard-direction"].forEach(id => byId(id).addEventListener("change", loadLeaderboard));
byId("search-size").addEventListener("change", event => {
    const candidates = Number(event.target.value);
    byId("max-candidates").value = candidates;
    byId("batch-size").value = Math.min(50, Math.max(10, Math.ceil(candidates / 5)));
});
byId("max-candidates").addEventListener("input", () => { byId("search-size").value = ""; });
byId("combination-policy").addEventListener("change", event => {
    const weighted = event.target.value === "WEIGHTED";
    byId("policy-threshold").disabled = !weighted;
    byId("policy-threshold-field").hidden = !weighted;
});
byId("policy-threshold").disabled = true;
window.cryptoLabDiscovery = { refreshUserStrategies: refreshDiscoveryUserStrategies };
byId("discovery-history").addEventListener("change", event => selectDiscoveryRun(event.target.value).catch(error => byId("search-message").textContent = error.message));
byId("refresh-discovery-history").addEventListener("click", () => loadDiscoveryHistory().catch(error => byId("search-message").textContent = error.message));
["leaderboard-sort","leaderboard-direction"].forEach(id => byId(id).addEventListener("change", () => loadAllTimeLeaderboard().catch(error => byId("search-message").textContent = error.message)));
loadCapabilities().catch(error => byId("search-message").textContent = error.message);
loadDiscoveryHistory().then(() => {
    if (labState.discoveryHistory[0] && !labState.searchRunId) return selectDiscoveryRun(labState.discoveryHistory[0].searchRunId);
}).catch(error => byId("search-message").textContent = error.message);
loadAllTimeLeaderboard().catch(error => byId("search-message").textContent = error.message);
refreshSystemStatus(); setInterval(refreshSystemStatus,5000); connectProofSocket();
