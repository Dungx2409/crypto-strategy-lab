const newsStatus = document.querySelector("#news-status");
const newsMessage = document.querySelector("#news-message");
const newsList = document.querySelector("#news-list");
const refreshNewsButton = document.querySelector("#refresh-news");
const analyzeNewsButton = document.querySelector("#analyze-news");
const saveNewsPreferencesButton = document.querySelector("#save-news-preferences");
const newsInterval = document.querySelector("#news-interval");
const newsCoin = document.querySelector("#news-coin");
let latestNewsItems = [];

function setNewsStatus(status, sentimentStatus) {
    newsStatus.textContent = `NEWS ${status} · SENTIMENT ${sentimentStatus}`;
    newsStatus.className = `status ${status === "UP"
        ? "status-online"
        : status === "DEGRADED" ? "status-degraded" : "status-offline"}`;
}

const sentimentIcons = {POSITIVE: "▲", NEUTRAL: "−", NEGATIVE: "▼", PENDING: "…"};

function collectionSummary(body, verb) {
    const parts = [
        `${verb} ${body.fetched ?? 0}`,
        `stored ${body.stored ?? 0}`,
        `analyzed ${body.analyzed ?? 0}`
    ];
    if (body.inferenceFailures) {
        parts.push(`failed ${body.inferenceFailures}`);
    }
    return parts.join(" · ");
}

function renderNews(items) {
    latestNewsItems = items.map(item => ({...item}));
    newsList.replaceChildren();
    const totals = {POSITIVE: 0, NEUTRAL: 0, NEGATIVE: 0};
    items.forEach(item => { if (totals[item.sentiment] !== undefined) totals[item.sentiment] += 1; });
    document.querySelector("#sentiment-positive").textContent = totals.POSITIVE;
    document.querySelector("#sentiment-neutral").textContent = totals.NEUTRAL;
    document.querySelector("#sentiment-negative").textContent = totals.NEGATIVE;
    if (!items.length) {
        const empty = document.createElement("p");
        empty.className = "news-empty";
        empty.textContent = "▤ No stored news is available yet.";
        newsList.append(empty);
        return;
    }
    items.forEach(item => {
        const article = document.createElement("article");
        article.className = "news-card";

        const heading = document.createElement("a");
        heading.href = item.url;
        heading.target = "_blank";
        heading.rel = "noopener noreferrer";
        heading.textContent = item.title;

        const meta = document.createElement("p");
        meta.className = "news-meta";
        meta.textContent = `📰 ${item.provider} · ${new Date(item.publishedAt).toLocaleString()}`;

        const sentiment = document.createElement("p");
        const key = item.sentiment || "PENDING";
        sentiment.className = `sentiment sentiment-${key.toLowerCase()}`;
        const icon = document.createElement("span");
        icon.className = "icon";
        icon.setAttribute("aria-hidden", "true");
        icon.textContent = sentimentIcons[key] || "…";
        sentiment.append(icon, document.createTextNode(item.sentiment
            ? `${item.sentiment} · score ${item.score} · ${item.modelName}@${item.modelVersion}`
            : "Sentiment pending"));

        article.append(heading, meta, sentiment);
        if (!item.sentiment) {
            const actions = document.createElement("div");
            actions.className = "button-row news-card-actions";
            const analyzeOne = document.createElement("button");
            analyzeOne.type = "button";
            analyzeOne.className = "secondary";
            analyzeOne.innerHTML = '<span class="btn-icon" aria-hidden="true">◉</span>Analyze sentiment';
            analyzeOne.addEventListener("click", () => analyzePendingNews());
            actions.append(analyzeOne);
            article.append(actions);
        }
        newsList.append(article);
    });
}

window.cryptoLabNews = {
    snapshot: () => latestNewsItems
            .filter(item => item.score !== null && item.score !== undefined && item.modelName && item.modelVersion)
            .map(item => ({
                sourceId: item.newsId,
                observedAt: item.publishedAt,
                score: item.score,
                modelName: item.modelName,
                modelVersion: item.modelVersion,
                inputVersion: item.inputVersion,
                preprocessingVersion: item.preprocessingVersion
            }))
};

async function loadNewsPreferences() {
    const response = await fetch("/api/v1/news/preferences");
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "News preferences request failed");
    if (newsInterval) newsInterval.value = body.interval || "5m";
    if (newsCoin) newsCoin.value = body.coin || "ALL";
    return body;
}

async function saveNewsPreferences() {
    saveNewsPreferencesButton.disabled = true;
    newsMessage.textContent = "Saving crawl preferences…";
    try {
        const response = await fetch("/api/v1/news/preferences", {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                interval: newsInterval.value,
                coin: newsCoin.value
            })
        });
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || "Could not save news preferences");
        newsInterval.value = body.interval;
        newsCoin.value = body.coin;
        newsMessage.dataset.keep = "true";
        newsMessage.textContent = body.categories
            ? `Auto-crawl every ${body.interval} for ${body.coin} (categories ${body.categories})`
            : `Auto-crawl every ${body.interval} for all coins`;
    } catch (error) {
        newsMessage.textContent = error.message;
    } finally {
        saveNewsPreferencesButton.disabled = false;
    }
}

async function loadStoredNews() {
    const response = await fetch("/api/v1/news?limit=20");
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "News request failed");
    setNewsStatus(body.providerStatus, body.sentimentStatus);
    if (!newsMessage.dataset.keep) {
        newsMessage.textContent = body.lastError || "";
    }
    delete newsMessage.dataset.keep;
    renderNews(body.items);
}

async function refreshNews() {
    refreshNewsButton.disabled = true;
    analyzeNewsButton.disabled = true;
    newsMessage.textContent = "Collecting articles and analyzing sentiment…";
    try {
        const response = await fetch("/api/v1/news/collect", {method: "POST"});
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || "News collection failed");
        newsMessage.dataset.keep = "true";
        newsMessage.textContent = collectionSummary(body, "Fetched")
            + (body.message ? ` · ${body.message}` : "");
        await loadStoredNews();
    } catch (error) {
        newsMessage.textContent = error.message;
        await loadStoredNews().catch(() => {});
    } finally {
        refreshNewsButton.disabled = false;
        analyzeNewsButton.disabled = false;
    }
}

async function analyzePendingNews() {
    refreshNewsButton.disabled = true;
    analyzeNewsButton.disabled = true;
    newsMessage.textContent = "Analyzing sentiment for stored articles without scores…";
    try {
        const response = await fetch("/api/v1/news/analyze?limit=20", {method: "POST"});
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || "Sentiment analysis failed");
        newsMessage.dataset.keep = "true";
        newsMessage.textContent = body.fetched === 0
            ? "No pending articles to analyze."
            : collectionSummary(body, "Queued") + (body.message ? ` · ${body.message}` : "");
        await loadStoredNews();
    } catch (error) {
        newsMessage.textContent = error.message;
        await loadStoredNews().catch(() => {});
    } finally {
        refreshNewsButton.disabled = false;
        analyzeNewsButton.disabled = false;
    }
}

refreshNewsButton.addEventListener("click", refreshNews);
analyzeNewsButton.addEventListener("click", analyzePendingNews);
saveNewsPreferencesButton.addEventListener("click", saveNewsPreferences);
["change"].forEach(eventName => {
    newsInterval.addEventListener(eventName, () => {
        newsMessage.textContent = "Click Apply to update the auto-crawl schedule.";
    });
    newsCoin.addEventListener(eventName, () => {
        newsMessage.textContent = "Click Apply to filter Collect & auto-crawl by this coin.";
    });
});

loadNewsPreferences()
    .catch(error => { newsMessage.textContent = error.message; })
    .finally(() => loadStoredNews().then(refreshNews).catch(error => {
        newsMessage.textContent = error.message;
        setNewsStatus("DOWN", "DOWN");
        renderNews([]);
    }));
