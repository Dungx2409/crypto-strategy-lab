function formatDateTime(value) {
    return value ? new Date(value).toLocaleString() : "—";
}

function shortId(value) {
    return value ? String(value).slice(0, 8) : "—";
}

function strategyName(strategy) {
    return strategy.displayLabel || strategy.type;
}

function strategyParameterText(parameters = {}) {
    const entries = Object.entries(parameters).map(([name, value]) => {
        if (name === "source") return `${name}: custom DSL`;
        if (typeof value === "object" && value !== null) return `${name}: ${JSON.stringify(value)}`;
        return `${name}: ${value}`;
    });
    return entries.length ? entries.join(", ") : "default parameters";
}

function strategyConfigText(strategies = []) {
    return strategies.map(strategy =>
        `${strategyName(strategy)}@${strategy.version} (${strategyParameterText(strategy.parameters)})`
    ).join(" + ") || "—";
}

function strategyShortText(strategies = []) {
    return strategies.map(strategy => `${strategyName(strategy)}@${strategy.version}`).join(" + ") || "—";
}

function experimentRunText(details) {
    return details.searchRunId ? `Discovery ${shortId(details.searchRunId)}` : `Manual backtest ${shortId(details.experimentId)}`;
}

function generatorText(generator) {
    return generator ? `${generator.type}@${generator.version}` : "manual@1.0";
}

function provenanceItem(label, value) {
    const item = document.createElement("article");
    item.className = "provenance-item";
    const name = document.createElement("span");
    name.textContent = label;
    const data = document.createElement("strong");
    data.textContent = value ?? "—";
    item.append(name, data);
    return item;
}

function leaderboardRunLabel(item) {
    return item.searchRunId ? `Discovery ${item.searchRunId.slice(0, 8)}` : `Manual ${item.experimentId.slice(0, 8)}`;
}

function appendLeaderboardRunCell(row, item) {
    const cell = row.insertCell();
    const button = document.createElement("button");
    button.type = "button";
    button.className = "link-button";
    button.textContent = leaderboardRunLabel(item);
    button.title = item.searchRunId
        ? "Open this discovery's leaderboard"
        : "Open this manual backtest result";
    button.addEventListener("click", event => {
        event.stopPropagation();
        if (item.searchRunId) {
            selectDiscoveryRun(item.searchRunId).then(() => {
                if (typeof showView === "function") showView("discovery");
                document.getElementById("leaderboard-title")?.scrollIntoView({block: "start"});
            }).catch(error => document.getElementById("search-message").textContent = error.message);
            return;
        }
        loadExperiment(item.experimentId);
        if (typeof showView === "function") showView("backtest");
    });
    cell.append(button);
}
