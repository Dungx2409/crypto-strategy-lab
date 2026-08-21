const viewCopy = new Map([...document.querySelectorAll(".view")].map(view => [view.id.replace("view-", ""), {title: view.dataset.title, subtitle: view.dataset.subtitle}]));

function showView(name) {
    const selected = viewCopy.has(name) ? name : "realtime";
    document.querySelectorAll(".view").forEach(view => view.classList.toggle("active", view.id === `view-${selected}`));
    document.querySelectorAll(".nav-item").forEach(item => item.classList.toggle("active", item.dataset.view === selected));
    document.querySelector("#view-title").textContent = viewCopy.get(selected).title;
    document.querySelector("#view-subtitle").textContent = viewCopy.get(selected).subtitle;
    history.replaceState(null, "", `#${selected}`);
}

document.querySelectorAll("[data-view]").forEach(control => control.addEventListener("click", event => {
    event.preventDefault();
    showView(control.dataset.view);
}));
window.addEventListener("hashchange", () => showView(location.hash.slice(1)));
showView(location.hash.slice(1));
