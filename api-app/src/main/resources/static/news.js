const newsStatus = document.querySelector("#news-status");
const newsMessage = document.querySelector("#news-message");
const newsList = document.querySelector("#news-list");
const refreshNewsButton = document.querySelector("#refresh-news");

function setNewsStatus(status, sentimentStatus) {
    newsStatus.textContent = `NEWS ${status} · SENTIMENT ${sentimentStatus}`;
    newsStatus.className = `status ${status === "UP"
        ? "status-online"
        : status === "DEGRADED" ? "status-degraded" : "status-offline"}`;
}

function renderNews(items) {
    newsList.replaceChildren();
    if (!items.length) {
        const empty = document.createElement("p");
        empty.className = "news-empty";
        empty.textContent = "No stored news is available yet.";
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
        meta.textContent = `${item.provider} · ${new Date(item.publishedAt).toLocaleString()}`;

        const sentiment = document.createElement("p");
        sentiment.className = `sentiment sentiment-${(item.sentiment || "pending").toLowerCase()}`;
        sentiment.textContent = item.sentiment
            ? `${item.sentiment} · score ${item.score} · ${item.modelName}@${item.modelVersion}`
            : "Sentiment pending";

        article.append(heading, meta, sentiment);
        newsList.append(article);
    });
}

async function loadStoredNews() {
    const response = await fetch("/api/v1/news?limit=20");
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "News request failed");
    setNewsStatus(body.providerStatus, body.sentimentStatus);
    newsMessage.textContent = body.lastError || "";
    renderNews(body.items);
}

async function refreshNews() {
    refreshNewsButton.disabled = true;
    newsMessage.textContent = "Collecting the latest news…";
    try {
        const response = await fetch("/api/v1/news/collect", {method: "POST"});
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || "News collection failed");
        await loadStoredNews();
    } catch (error) {
        newsMessage.textContent = error.message;
        await loadStoredNews().catch(() => {});
    } finally {
        refreshNewsButton.disabled = false;
    }
}

refreshNewsButton.addEventListener("click", refreshNews);
loadStoredNews().then(refreshNews).catch(error => {
    newsMessage.textContent = error.message;
    setNewsStatus("DOWN", "DOWN");
    renderNews([]);
});
