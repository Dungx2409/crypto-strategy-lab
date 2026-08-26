const form = document.querySelector("#account-form");
const logout = document.querySelector("#account-logout");
const summary = document.querySelector("#account-summary");
const message = document.querySelector("#account-message");
let csrf = null;

async function request(url, options = {}) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `${response.status} ${response.statusText}`);
    return body;
}

function showAccount(account) {
    form.hidden = Boolean(account);
    logout.hidden = !account;
    summary.querySelector("strong").textContent = account?.username || "Guest";
    summary.querySelector("small").textContent = account
        ? account.role
        : "Sign in to run experiments";
    document.dispatchEvent(new CustomEvent("crypto-lab:account", {detail: account}));
}

async function authenticate(path) {
    message.textContent = "";
    const account = await request(path, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            username: document.querySelector("#account-username").value,
            password: document.querySelector("#account-password").value
        })
    });
    csrf = null;
    showAccount(account);
}

async function csrfHeaders() {
    if (!csrf) csrf = await request("/api/v1/auth/csrf");
    return {[csrf.headerName]: csrf.token};
}

form.addEventListener("submit", event => {
    event.preventDefault();
    authenticate("/api/v1/auth/login").catch(error => message.textContent = error.message);
});
document.querySelector("#account-register").addEventListener("click", () =>
    authenticate("/api/v1/auth/register").catch(error => message.textContent = error.message));
logout.addEventListener("click", async () => {
    await request("/api/v1/auth/logout", {method: "POST", headers: await csrfHeaders()});
    csrf = null;
    showAccount(null);
});

window.cryptoLabAuth = {csrfHeaders};
request("/api/v1/auth/me").then(showAccount).catch(() => showAccount(null));
