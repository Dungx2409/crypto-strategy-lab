const accountFeatureState = {account: null, draftId: null, strategies: [], editingScheduleId: null};

function featureButton(label, action, secondary = false, icon = "") {
    const button = document.createElement("button"); button.type = "button";
    if (icon) {
        const mark = document.createElement("span"); mark.className = "btn-icon"; mark.setAttribute("aria-hidden", "true"); mark.textContent = icon;
        button.append(mark, document.createTextNode(label));
    } else {
        button.textContent = label;
    }
    if (secondary) button.className = "secondary"; button.addEventListener("click", action); return button;
}

function setAccount(account) {
    accountFeatureState.account = account;
    byId("auth-gate").hidden = Boolean(account);
    byId("app-shell").hidden = !account;
    byId("account-name").textContent = account?.username || "Guest";
    byId("account-state").textContent = account ? "Session account" : "Sign in for saved work";
    byId("auth-status").textContent = account ? "SIGNED IN" : "SIGNED OUT";
    byId("auth-status").className = `status ${account ? "status-online" : "status-offline"}`;
    byId("auth-credentials").hidden = Boolean(account);
    byId("register-account").hidden = Boolean(account);
    byId("login-account").hidden = Boolean(account);
    byId("logout-account").hidden = !account;
    byId("logout-account").disabled = !account;
    byId("authoring-status").textContent = account ? "READY" : "SIGN IN";
    byId("authoring-status").className = `status ${account ? "status-online" : "status-offline"}`;
}

async function refreshAccount() {
    try { setAccount(await api("/api/v1/auth/me")); }
    catch (_) { setAccount(null); return; }
    await Promise.all([loadSavedStrategies(), loadSchedules(), loadCrawlerTemplates(), loadManualHistory()])
        .catch(error => byId("auth-message").textContent = error.message);
}

async function authenticate(path, usernameId = "auth-username", passwordId = "auth-password", messageId = "auth-message") {
    try {
        const account = await api(`/api/v1/auth/${path}`, {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({username:byId(usernameId).value,password:byId(passwordId).value})});
        setAccount(account); byId(messageId).textContent = `Signed in as ${account.username}.`; byId(passwordId).value = ""; showView("realtime");
        await Promise.all([loadSavedStrategies(), loadSchedules(), loadCrawlerTemplates(), loadManualHistory()]);
    } catch (error) { byId(messageId).textContent = error.message; }
}

async function logout() {
    await api("/api/v1/auth/logout", {method:"POST"}); setAccount(null);
    accountFeatureState.strategies = []; accountFeatureState.draftId = null;
    renderSavedStrategies(); renderSchedules([]); renderCrawlerTemplates([]);
    byId("manual-history-count").textContent = "";
    renderManualHistory([]);
    byId("strategy-idea").hidden = true;
    byId("strategy-source-preview").hidden = true;
    byId("save-strategy").disabled = true;
}

function setAuthoringMode(mode) {
    const article = mode === "article";
    byId("authoring-source").value = article ? "article" : "prompt";
    document.querySelectorAll("[data-authoring-mode]").forEach(button => {
        button.classList.toggle("active", button.dataset.authoringMode === (article ? "article" : "prompt"));
    });
    byId("article-url-field").hidden = !article;
    byId("strategy-prompt-field").hidden = article;
    byId("propose-strategy").innerHTML = article
        ? '<span class="btn-icon" aria-hidden="true">🔗</span>Generate from URL'
        : '<span class="btn-icon" aria-hidden="true">✦</span>Generate idea';
    byId("authoring-message").textContent = article
        ? "Paste a public article URL. Gemini will read it and draft a strategy idea for confirmation."
        : "Describe the strategy in plain language, then generate an idea to review.";
}

async function proposeStrategy() {
    const article = byId("authoring-source").value === "article";
    if (article && !byId("article-url").value.trim()) {
        byId("authoring-message").textContent = "Enter a public article URL first.";
        byId("article-url").focus();
        return;
    }
    if (!article && !byId("strategy-prompt").value.trim()) {
        byId("authoring-message").textContent = "Enter a strategy prompt first.";
        byId("strategy-prompt").focus();
        return;
    }
    const body = article ? {articleUrl: byId("article-url").value.trim()} : {prompt: byId("strategy-prompt").value.trim()};
    try {
        byId("propose-strategy").disabled = true;
        byId("confirm-strategy").disabled = true;
        byId("save-strategy").disabled = true;
        byId("authoring-message").textContent = article
            ? "Reading the article URL and asking Gemini…"
            : "Asking Gemini for an idea…";
        const draft = await api("/api/v1/user-strategies/drafts", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(body)
        });
        accountFeatureState.draftId = draft.id;
        byId("strategy-idea").hidden = false;
        byId("strategy-idea").textContent = draft.idea;
        byId("strategy-source-preview").hidden = true;
        byId("confirm-strategy").disabled = false;
        byId("authoring-message").textContent = "Review the idea, then build and test its code.";
    } catch (error) {
        byId("authoring-message").textContent = error.message;
    } finally {
        byId("propose-strategy").disabled = false;
    }
}

async function confirmStrategy() {
    try {
        byId("confirm-strategy").disabled = true; byId("authoring-message").textContent = "Building, validating, and smoke-testing Trading DSL…";
        const draft = await api(`/api/v1/user-strategies/drafts/${accountFeatureState.draftId}/build`, {method:"POST"});
        const sources = draft.preview.strategies.filter(item=>item.type==="AI_DSL").map(item=>item.parameters.source);
        byId("strategy-source-preview").textContent = sources.length?sources.join("\n\n"):JSON.stringify(draft.preview,null,2);
        byId("strategy-source-preview").hidden = false; byId("save-strategy").disabled = false;
        byId("authoring-message").textContent = "The code passed the 250-candle smoke test. Review it before saving.";
    } catch (error) { byId("authoring-message").textContent = error.message; byId("confirm-strategy").disabled = false; }
}

async function saveStrategy() {
    try {
        byId("save-strategy").disabled = true;
        const saved = await api(`/api/v1/user-strategies/drafts/${accountFeatureState.draftId}/confirm`, {method:"POST"});
        byId("authoring-message").textContent = `${saved.document.name} version ${saved.version} is ready.`;
        byId("strategy-source-preview").hidden = true; accountFeatureState.draftId = null;
        await loadSavedStrategies();
    } catch (error) { byId("authoring-message").textContent = error.message; byId("save-strategy").disabled = false; }
}

async function loadSavedStrategies() {
    accountFeatureState.strategies = await api("/api/v1/user-strategies"); renderSavedStrategies();
}

function renderSavedStrategies() {
    const host = byId("saved-strategies"), select = byId("manual-strategy"); host.replaceChildren(); select.replaceChildren();
    if (!accountFeatureState.strategies.length) {
        host.textContent = accountFeatureState.account ? "⌘ No saved strategies." : "⇢ Sign in to load saved strategies.";
        const option = document.createElement("option"); option.value=""; option.textContent="No saved strategy"; select.append(option); return;
    }
    accountFeatureState.strategies.forEach(strategy => {
        const row=document.createElement("article"); row.className="saved-row";
        const text=document.createElement("div"); text.innerHTML=`<strong></strong><small></small>`; text.querySelector("strong").textContent=`⌘ ${strategy.document.name} v${strategy.version}`; text.querySelector("small").textContent=strategy.document.strategies.map(item=>item.type).join(" + ");
        row.append(text,featureButton("Delete",async()=>{await api(`/api/v1/user-strategies/${strategy.id}`,{method:"DELETE"});await loadSavedStrategies();},true,"✕")); host.append(row);
        const option=document.createElement("option"); option.value=strategy.id; option.textContent=`${strategy.document.name} v${strategy.version}`; select.append(option);
    });
}

function scheduleBody() { return {symbol:byId("schedule-symbol").value,timeframe:byId("schedule-timeframe").value,lookback:`P${byId("schedule-lookback").value}D`,initialCapital:Number(byId("schedule-capital").value),candidateLimit:Number(byId("schedule-candidates").value),interval:`PT${byId("schedule-interval").value}H`}; }
function applySchedulePresets() {
    byId("schedule-lookback").value = byId("schedule-lookback-preset").value;
    byId("schedule-interval").value = byId("schedule-frequency-preset").value;
}
async function saveSchedule() {
    try {
        const id=accountFeatureState.editingScheduleId, url=id?`/api/v1/discovery-schedules/${id}`:"/api/v1/discovery-schedules";
        await api(url,{method:id?"PUT":"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(scheduleBody())});
        accountFeatureState.editingScheduleId=null; byId("create-schedule").textContent="Create schedule"; await loadSchedules();
    } catch(error){byId("schedule-message").textContent=error.message;}
}
async function loadSchedules(){renderSchedules(await api("/api/v1/discovery-schedules"));}
function renderSchedules(items){const host=byId("schedule-list");host.replaceChildren();if(!items.length){host.textContent=accountFeatureState.account?"◷ No schedules.":"⇢ Sign in to manage schedules.";return;}items.forEach(item=>{const row=document.createElement("article");row.className="saved-row";const text=document.createElement("div");text.innerHTML="<strong></strong><small></small>";text.querySelector("strong").textContent=`◷ ${item.symbol} ${item.timeframe} · ${item.status}`;text.querySelector("small").textContent=`Runs ${item.completedRuns} · next ${new Date(item.nextRunAt).toLocaleString()}${item.lastError?` · ${item.lastError}`:""}`;const actions=document.createElement("div");actions.className="button-row";actions.append(featureButton(item.status==="ACTIVE"?"Stop":"Start",async()=>{await api(`/api/v1/discovery-schedules/${item.id}/${item.status==="ACTIVE"?"stop":"start"}`,{method:"POST"});await loadSchedules();},true,item.status==="ACTIVE"?"■":"▶"),featureButton("Edit",()=>{accountFeatureState.editingScheduleId=item.id;byId("schedule-symbol").value=item.symbol;byId("schedule-timeframe").value=item.timeframe;byId("schedule-lookback").value=Math.round(Number(item.lookback)/86400)||365;byId("schedule-capital").value=item.initialCapital;byId("schedule-candidates").value=item.candidateLimit;byId("schedule-interval").value=Math.round(Number(item.interval)/3600)||24;byId("schedule-lookback-preset").value=byId("schedule-lookback").value;byId("schedule-frequency-preset").value=byId("schedule-interval").value;byId("schedule-lookback").closest("details").open=true;byId("create-schedule").textContent="Save changes";},true,"✎"),featureButton("Versions",async()=>{const versions=await api(`/api/v1/discovery-schedules/${item.id}/versions`);byId("schedule-message").textContent=versions.map(v=>`v${v.version} ${v.symbol} ${v.timeframe}`).join(" · ");},true,"☰"));row.append(text,actions);host.append(row);});}

function localDateTime(date) {
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
        .toISOString().slice(0, 16);
}

function applyBacktestPeriod() {
    const period = byId("manual-period").value;
    if (period === "custom") {
        byId("backtest-advanced").open = true;
        return;
    }
    const to = new Date();
    const from = new Date(to.getTime() - Number(period) * 86400000);
    byId("manual-from").value = localDateTime(from);
    byId("manual-to").value = localDateTime(to);
}

function applyRiskProfile() {
    const profile = byId("backtest-risk-profile").value;
    const profiles = {
        standard: {size:"100", stop:"", take:"", trail:"", short:false},
        conservative: {size:"50", stop:"2", take:"4", trail:"", short:false},
        active: {size:"75", stop:"3", take:"6", trail:"2", short:true}
    };
    if (profile === "custom") {
        byId("backtest-advanced").open = true;
        return;
    }
    const values = profiles[profile];
    byId("position-size-pct").value = values.size;
    byId("stop-loss-pct").value = values.stop;
    byId("take-profit-pct").value = values.take;
    byId("trailing-stop-pct").value = values.trail;
    byId("allow-short").checked = values.short;
}

function setManualBacktestProgress(message, percent, running = true) {
    const button = byId("run-manual-backtest");
    const status = byId("manual-backtest-message");
    const track = byId("manual-backtest-progress");
    const bar = byId("manual-backtest-progress-bar");
    status.textContent = message;
    byId("experiment-message").textContent = message;
    track.hidden = !running && percent <= 0;
    bar.style.width = `${Math.max(0, Math.min(100, percent))}%`;
    button.disabled = running;
    if (running) {
        byId("experiment-rank").textContent = "RUNNING";
        byId("experiment-rank").className = "status status-degraded";
    }
}

async function runManualBacktest() {
    const button = byId("run-manual-backtest");
    if (button.disabled) return;
    try {
        setManualBacktestProgress("Validating backtest inputs…", 8, true);
        const strategy = accountFeatureState.strategies.find(item => item.id === byId("manual-strategy").value);
        if (!strategy) throw new Error("Choose a saved strategy.");
        const fromValue = byId("manual-from").value, toValue = byId("manual-to").value;
        if (Boolean(fromValue) !== Boolean(toValue)) throw new Error("Choose both From and To, or leave both blank.");
        const query = new URLSearchParams({
            symbol: byId("manual-symbol").value,
            timeframe: byId("manual-timeframe").value,
            limit: "1000"
        });
        if (fromValue) {
            const from = new Date(fromValue), to = new Date(toValue);
            if (from >= to) throw new Error("From must be earlier than To.");
            query.set("from", from.toISOString());
            query.set("to", to.toISOString());
        }
        setManualBacktestProgress(`Loading ${byId("manual-symbol").value} ${byId("manual-timeframe").value} candles…`, 25, true);
        const market = await api(`/api/v1/market/candles?${query}`);
        const candles = market.candles;
        if (candles.length < 2) throw new Error("The selected date range needs at least two candles.");
        const optional = id => byId(id).value === "" ? null : Number(byId(id).value);
        const body = {
            symbol: market.symbol,
            timeframe: market.timeframe,
            datasetVersion: `manual-${Date.now()}`,
            candles,
            strategies: strategy.document.strategies,
            combinationPolicy: strategy.document.combinationPolicy,
            executionConfig: {
                initialCapital: Number(byId("initial-capital").value),
                feeRate: Number(byId("fee-rate").value),
                allowShort: byId("allow-short").checked,
                fillPolicy: labState.capabilities.fillPolicy,
                engineVersion: labState.capabilities.engineVersion,
                positionSizePct: Number(byId("position-size-pct").value),
                stopLossPct: optional("stop-loss-pct"),
                takeProfitPct: optional("take-profit-pct"),
                trailingStopPct: optional("trailing-stop-pct")
            },
            generator: {
                type: "manual",
                version: "1.0",
                configuration: {source: "account-strategy", from: fromValue || null, to: toValue || null},
                randomSeed: null
            }
        };
        setManualBacktestProgress(`Running backtest on ${candles.length} candles…`, 55, true);
        const result = await api("/api/v1/experiments", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(body)
        });
        setManualBacktestProgress("Loading trades, metrics, and chart…", 85, true);
        await loadExperiment(result.experimentId);
        await loadManualHistory();
        setManualBacktestProgress(`Backtest complete · ${candles.length} candles`, 100, false);
        byId("manual-backtest-progress").hidden = false;
        setTimeout(() => {
            byId("manual-backtest-progress").hidden = true;
            byId("manual-backtest-progress-bar").style.width = "0%";
        }, 1200);
    } catch (error) {
        setManualBacktestProgress(error.message, 0, false);
        byId("experiment-rank").textContent = "FAILED";
        byId("experiment-rank").className = "status status-offline";
        byId("manual-backtest-progress").hidden = true;
        byId("manual-backtest-progress-bar").style.width = "0%";
    } finally {
        byId("run-manual-backtest").disabled = false;
    }
}
async function loadManualHistory(){const items=await api("/api/v1/experiments/mine");byId("manual-history-count").textContent=`${items.length} saved manual runs`;renderManualHistory(items);}
function renderManualHistory(items){const host=byId("manual-history-list");host.replaceChildren();if(!items.length){host.textContent=accountFeatureState.account?"▥ No saved manual runs.":"⇢ Sign in to load saved runs.";return;}items.forEach(item=>{const row=document.createElement("article");row.className="saved-row";const text=document.createElement("div");text.innerHTML="<strong></strong><small></small>";text.querySelector("strong").textContent=`▥ ${item.dataset.symbol} ${item.dataset.timeframe} · ${item.status}`;text.querySelector("small").textContent=item.metrics?`Return ${item.metrics.totalReturnPct}% · Win ${item.metrics.winRatePct}% · ${item.metrics.totalTrades} trades`:"Result is not complete";row.append(text,featureButton("Load result",()=>loadExperiment(item.experimentId),true,"⇢"));host.append(row);});}
function applyTradeFilters(){const details=window.cryptoLabCurrentExperiment;if(!details)return;const minimum=byId("trade-filter-pnl").value===""?-Infinity:Number(byId("trade-filter-pnl").value),direction=byId("trade-filter-direction").value,reason=byId("trade-filter-reason").value;window.cryptoLabBacktest.render({...details,trades:(details.trades||[]).filter(trade=>Number(trade.pnl)>=minimum&&(!direction||trade.direction===direction)&&(!reason||trade.exitReason===reason))});}

async function loadCrawlerTemplates(){renderCrawlerTemplates(await api("/api/v1/crawler-templates"));}
async function createCrawlerTemplate(){try{await api("/api/v1/crawler-templates",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({siteUrl:byId("crawler-site-url").value,itemSelector:byId("crawler-item-selector").value,titleSelector:byId("crawler-title-selector").value,linkSelector:byId("crawler-link-selector").value,dateSelector:byId("crawler-date-selector").value})});await loadCrawlerTemplates();}catch(error){byId("crawler-message").textContent=error.message;}}
function renderCrawlerTemplates(items){const host=byId("crawler-template-list");host.replaceChildren();if(!items.length){host.textContent=accountFeatureState.account?"🌐 No crawler templates.":"⇢ Sign in to manage templates.";return;}items.forEach(item=>{const row=document.createElement("article");row.className="saved-row";const text=document.createElement("div");text.innerHTML="<strong></strong><small></small>";text.querySelector("strong").textContent=`🌐 ${item.siteUrl} · v${item.version}`;text.querySelector("small").textContent=`${item.selectors.itemSelector} | ${item.selectors.titleSelector}`;const actions=document.createElement("div");actions.className="button-row";actions.append(featureButton("Collect articles",async()=>{try{const result=await api(`/api/v1/crawler-templates/${item.templateId}/collect`,{method:"POST"});byId("crawler-message").textContent=`Crawled ${result.fetched}, stored ${result.stored}, sentiment analyzed ${result.analyzed}${result.inferenceFailures?`, failed ${result.inferenceFailures}`:""}.`;const status=byId("news-message");if(status){status.dataset.keep="true";status.textContent=byId("crawler-message").textContent;}await loadStoredNews();}catch(error){byId("crawler-message").textContent=error.message;}},true,"⬇"),featureButton("Check now",async()=>{try{const result=await api(`/api/v1/crawler-templates/${item.templateId}/check`,{method:"POST"});if(result.status==="NEEDS_REVIEW"){byId("crawler-message").replaceChildren(document.createTextNode(`HTML changed. Review v${result.version}: ${JSON.stringify(result.selectors)} `),featureButton("Confirm",async()=>{await api(`/api/v1/crawler-templates/${item.templateId}/versions/${result.version}/confirm`,{method:"POST"});await loadCrawlerTemplates();},false,"✓"));}else byId("crawler-message").textContent="Active selectors still match.";}catch(error){byId("crawler-message").textContent=error.message;}},true,"◎"),featureButton("Repair with Gemini",async()=>{const sample=window.prompt("Paste the changed HTML sample");if(!sample)return;try{const repaired=await api(`/api/v1/crawler-templates/${item.templateId}/repair`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sampleHtml:sample,failure:"Stored selectors no longer match"})});byId("crawler-message").replaceChildren(document.createTextNode(`Review v${repaired.version}: ${JSON.stringify(repaired.selectors)} `),featureButton("Confirm",async()=>{await api(`/api/v1/crawler-templates/${item.templateId}/versions/${repaired.version}/confirm`,{method:"POST"});await loadCrawlerTemplates();},false,"✓"));}catch(error){byId("crawler-message").textContent=error.message;}},true,"✦"));row.append(text,actions);host.append(row);});}

byId("register-account").addEventListener("click",()=>authenticate("register"));byId("login-account").addEventListener("click",()=>authenticate("login"));byId("logout-account").addEventListener("click",logout);
byId("auth-gate-form").addEventListener("submit",event=>{event.preventDefault();authenticate("login","auth-gate-username","auth-gate-password","auth-gate-message");});
byId("auth-gate-register").addEventListener("click",()=>authenticate("register","auth-gate-username","auth-gate-password","auth-gate-message"));
document.querySelectorAll("[data-authoring-mode]").forEach(button => {
    button.addEventListener("click", () => setAuthoringMode(button.dataset.authoringMode));
});
byId("authoring-source").addEventListener("change", event => setAuthoringMode(event.target.value));
byId("propose-strategy").addEventListener("click", proposeStrategy);
byId("confirm-strategy").addEventListener("click", confirmStrategy);
byId("save-strategy").addEventListener("click", saveStrategy);
setAuthoringMode(byId("authoring-source").value || "prompt");
byId("create-schedule").addEventListener("click",saveSchedule);byId("run-manual-backtest").addEventListener("click",runManualBacktest);byId("apply-trade-filters").addEventListener("click",applyTradeFilters);byId("create-crawler-template").addEventListener("click",createCrawlerTemplate);
byId("schedule-lookback-preset").addEventListener("change",applySchedulePresets);byId("schedule-frequency-preset").addEventListener("change",applySchedulePresets);
byId("manual-period").addEventListener("change",applyBacktestPeriod);byId("backtest-risk-profile").addEventListener("change",applyRiskProfile);
["schedule-lookback","schedule-interval"].forEach(id=>byId(id).addEventListener("input",()=>{byId(id==="schedule-lookback"?"schedule-lookback-preset":"schedule-frequency-preset").value="";}));
["manual-from","manual-to"].forEach(id=>byId(id).addEventListener("input",()=>byId("manual-period").value="custom"));
["position-size-pct","stop-loss-pct","take-profit-pct","trailing-stop-pct","allow-short"].forEach(id=>byId(id).addEventListener("input",()=>byId("backtest-risk-profile").value="custom"));
applySchedulePresets();applyBacktestPeriod();applyRiskProfile();
refreshAccount();
