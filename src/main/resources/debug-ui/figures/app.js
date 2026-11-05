"use strict";

const BASE = window.location.pathname.replace(/\/+$/, "");
const state = {
    runs: [],
    run: null,
    selectedId: null,
    tab: "figures",
    filter: "all",
    query: "",
    documentView: "compare",
    syncScroll: true,
    loading: false
};

const elements = {
    list: document.getElementById("run-list"),
    count: document.getElementById("run-count"),
    search: document.getElementById("run-search"),
    refresh: document.getElementById("refresh-runs"),
    empty: document.getElementById("empty-state"),
    detail: document.getElementById("run-detail"),
    title: document.getElementById("run-title"),
    id: document.getElementById("run-id"),
    updated: document.getElementById("run-updated"),
    source: document.getElementById("source-link"),
    stats: document.getElementById("run-stats"),
    content: document.getElementById("tab-content"),
    toast: document.getElementById("toast")
};

let toastTimer;

function el(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined && text !== null) element.textContent = text;
    return element;
}

function clear(element) {
    element.replaceChildren();
}

function formatDate(value) {
    if (!value) return "Unknown time";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, {
        month: "short", day: "numeric", hour: "2-digit", minute: "2-digit"
    }).format(date);
}

function domain(value) {
    const url = safeUrl(value);
    return url ? url.hostname.replace(/^www\./, "") : "Debug artifact";
}

function safeUrl(value) {
    if (!value) return null;
    try {
        const url = new URL(value);
        return ["http:", "https:"].includes(url.protocol) ? url : null;
    } catch {
        return null;
    }
}

function externalLink(value, className, label) {
    const url = safeUrl(value);
    if (!url) return el("span", className, label || value || "Not captured");
    const link = el("a", className, label || url.toString());
    link.href = url.toString();
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    return link;
}

function statusBadge(figure) {
    let label = figure.status || "not-captured";
    if (figure.status === "cached") label = figure.knowledge ? "Cached · described" : "Cached · NONE";
    if (figure.status === "not-captured") label = "Not captured";
    return el("span", `status-badge ${figure.status || "not-captured"}`, label.replaceAll("-", " "));
}

function stat(value, label) {
    const item = el("div", "stat");
    item.append(el("div", "stat-value", String(value)), el("div", "stat-label", label));
    return item;
}

function emptyPanel(title, message) {
    const panel = el("div", "empty-panel");
    panel.append(el("strong", "", title), document.createTextNode(message));
    return panel;
}

function showError(message) {
    clearTimeout(toastTimer);
    elements.toast.textContent = message;
    elements.toast.hidden = false;
    toastTimer = setTimeout(() => { elements.toast.hidden = true; }, 5000);
}

async function api(path) {
    const response = await fetch(`${BASE}/api${path}`, {headers: {"Accept": "application/json"}});
    if (!response.ok) throw new Error(`Debug request failed (${response.status})`);
    return response.json();
}

async function loadRuns(preferredId) {
    if (state.loading) return;
    state.loading = true;
    elements.refresh.disabled = true;
    try {
        state.runs = await api("/runs");
        renderRunList();
        const hashId = decodeURIComponent(window.location.hash.slice(1));
        const wanted = preferredId || state.selectedId || hashId;
        const selected = state.runs.find(run => run.id === wanted) || state.runs[0];
        if (selected) {
            if (!state.run || state.run.id !== selected.id || preferredId) await selectRun(selected.id, false);
        } else {
            state.run = null;
            state.selectedId = null;
            elements.detail.hidden = true;
            elements.empty.hidden = false;
        }
    } catch (error) {
        showError(error.message);
        elements.list.replaceChildren(el("div", "sidebar-message", "Could not load debug runs."));
    } finally {
        state.loading = false;
        elements.refresh.disabled = false;
    }
}

async function selectRun(id, updateHash = true) {
    try {
        state.selectedId = id;
        state.run = await api(`/runs/${encodeURIComponent(id)}`);
        if (updateHash) history.replaceState(null, "", `#${encodeURIComponent(id)}`);
        renderRunList();
        renderRun();
    } catch (error) {
        showError(error.message);
    }
}

function filteredRuns() {
    const query = state.query.toLowerCase();
    return state.runs.filter(run => {
        const matchesQuery = !query || `${run.title || ""} ${run.articleUrl || ""} ${run.id}`.toLowerCase().includes(query);
        const matchesFilter = state.filter === "all"
            || state.filter === "issues" && (run.failed > 0 || run.none > 0)
            || state.filter === "none" && run.none > 0
            || state.filter === "cached" && run.cached > 0;
        return matchesQuery && matchesFilter;
    });
}

function renderRunList() {
    clear(elements.list);
    const runs = filteredRuns();
    elements.count.textContent = `${runs.length} of ${state.runs.length} runs`;
    if (!runs.length) {
        elements.list.append(el("div", "sidebar-message", state.runs.length ? "No runs match this filter." : "No debug runs found. Process an article with figure tracing enabled."));
        return;
    }
    for (const run of runs) {
        const card = el("button", `run-card${run.id === state.selectedId ? " active" : ""}`);
        card.type = "button";
        card.dataset.runId = run.id;
        card.append(el("div", "run-card-title", run.title || "Untitled debug run"));
        card.append(el("div", "run-card-domain", domain(run.articleUrl)));
        const meta = el("div", "run-card-meta");
        meta.append(el("span", "mini-stat", `${run.figures} fig`));
        if (run.none) meta.append(el("span", "mini-stat none", `${run.none} NONE`));
        if (run.failed) meta.append(el("span", "mini-stat failed", `${run.failed} error`));
        if (run.cached) meta.append(el("span", "mini-stat cached", `${run.cached} cached`));
        meta.append(el("span", "run-card-time", formatDate(run.updatedAt)));
        card.append(meta);
        elements.list.append(card);
    }
}

function renderRun() {
    const run = state.run;
    elements.empty.hidden = true;
    elements.detail.hidden = false;
    elements.title.textContent = run.title || "Untitled debug run";
    elements.id.textContent = run.id;
    elements.updated.textContent = formatDate(run.updatedAt);
    const source = safeUrl(run.articleUrl);
    elements.source.hidden = !source;
    if (source) elements.source.href = source.toString();

    clear(elements.stats);
    const recovered = run.figures.filter(figure => figure.knowledge).length;
    const inserted = run.insertions.filter(insertion => insertion.startOffset >= 0).length;
    elements.stats.append(
        stat(run.figures.length, "Figures"),
        stat(`${recovered}/${run.figures.length}`, "Recovered"),
        stat(inserted, "Insertions"),
        stat(run.originalWordCount.toLocaleString(), "Original words"),
        stat(run.enrichedWordCount.toLocaleString(), "Enriched words"),
        stat(run.summaryWordCount.toLocaleString(), "Summary words")
    );
    document.getElementById("figures-tab-count").textContent = run.figures.length;
    document.getElementById("insertions-tab-count").textContent = inserted;
    document.getElementById("decisions-tab-count").textContent = run.decisions.length;
    document.querySelectorAll(".view-tab").forEach(tab => tab.classList.toggle("active", tab.dataset.tab === state.tab));
    renderTab();
}

function renderTab() {
    clear(elements.content);
    if (state.tab === "figures") renderFigures();
    if (state.tab === "insertions") renderInsertions();
    if (state.tab === "decisions") renderDecisions();
    if (state.tab === "documents") renderDocuments();
}

function imageUrl(ordinal) {
    return `${BASE}/api/runs/${encodeURIComponent(state.run.id)}/figures/${ordinal}/image`;
}

function evidenceField(label, value, isLink = false) {
    const field = el("div", "evidence-field");
    field.append(el("span", "evidence-label", label));
    const content = el("div", "evidence-value");
    content.append(isLink ? externalLink(value, "", value || "Not captured") : document.createTextNode(value || "Not captured"));
    field.append(content);
    return field;
}

function renderFigures() {
    const figures = state.run.figures;
    if (!figures.length) {
        elements.content.append(emptyPanel("No figure artifacts", "This run contains no selected figures or captured vision outcomes."));
        return;
    }
    for (const figure of figures) {
        const card = el("article", "figure-card");
        card.id = `figure-${figure.ordinal}`;
        const header = el("header", "card-header");
        header.append(el("span", "card-number", `Figure ${figure.ordinal}`), statusBadge(figure));
        const spacer = el("span", "card-header-spacer");
        header.append(spacer);
        if (state.run.insertions.some(insertion => insertion.figureOrdinal === figure.ordinal && insertion.startOffset >= 0)) {
            const jump = el("button", "action-button", "View insertion");
            jump.type = "button";
            jump.dataset.action = "view-insertion";
            jump.dataset.ordinal = figure.ordinal;
            header.append(jump);
        }
        card.append(header);

        const grid = el("div", "figure-grid");
        const visual = el("div", "figure-visual");
        if (figure.hasImage) {
            const link = el("a");
            link.href = imageUrl(figure.ordinal);
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            const image = el("img");
            image.src = imageUrl(figure.ordinal);
            image.alt = figure.altText || `Figure ${figure.ordinal}`;
            image.loading = "lazy";
            link.append(image);
            visual.append(link);
        } else {
            visual.append(el("div", "missing-image", "No image was saved. The fetch may have failed before vision ran."));
        }
        grid.append(visual);

        const evidence = el("div", "figure-evidence");
        evidence.append(el("p", "section-label", "Article evidence"));
        evidence.append(evidenceField("Caption", figure.caption));
        evidence.append(evidenceField("Alt text", figure.altText));
        evidence.append(evidenceField("Paragraph before", figure.precedingParagraph));
        evidence.append(evidenceField("Paragraph after", figure.followingParagraph));
        evidence.append(evidenceField("Image URL", figure.imageUrl, true));
        grid.append(evidence);

        const output = el("div", "figure-output");
        output.append(el("p", "section-label", "Recovered knowledge"));
        if (figure.knowledge) {
            output.append(el("p", "knowledge-text", figure.knowledge));
        } else if (figure.error) {
            output.append(el("div", "output-message error", figure.error));
        } else if (figure.status === "none" || figure.status === "cached") {
            output.append(el("div", "output-message", "The vision outcome was NONE, so no knowledge was inserted."));
        } else {
            output.append(el("div", "output-message", "No vision outcome was captured for this figure."));
        }
        const meta = el("div", "model-meta");
        if (figure.model) meta.append(el("span", "", figure.model));
        if (figure.mediaType) meta.append(el("span", "", figure.mediaType));
        if (figure.totalTokens !== null) meta.append(el("span", "", `${figure.totalTokens} tokens`));
        if (figure.imageSha256) meta.append(el("span", "", `sha256 ${figure.imageSha256.slice(0, 12)}…`));
        if (meta.childNodes.length) output.append(meta);
        if (figure.prompt) {
            const details = el("details", "prompt-details");
            details.append(el("summary", "", "Exact vision prompt"));
            details.append(el("pre", "", figure.prompt));
            output.append(details);
        }
        if (figure.response && figure.response !== figure.knowledge) {
            const details = el("details", "prompt-details");
            details.append(el("summary", "", "Raw response"));
            details.append(el("pre", "raw-response", figure.response));
            output.append(details);
        }
        grid.append(output);
        card.append(grid);
        elements.content.append(card);
    }
}

function figureFor(ordinal) {
    return state.run.figures.find(figure => figure.ordinal === ordinal);
}

function renderInsertions() {
    const insertions = state.run.insertions;
    if (!insertions.length) {
        elements.content.append(emptyPanel("No insertions captured", "Processed figures will record their exact enriched-article location here."));
        return;
    }
    for (const insertion of insertions) {
        const figure = figureFor(insertion.figureOrdinal);
        const inserted = insertion.startOffset >= 0;
        const card = el("article", "insertion-card");
        const header = el("header", "card-header");
        header.append(el("span", "card-number", `Figure ${insertion.figureOrdinal}`));
        header.append(el("span", `status-badge ${inserted ? "" : "not-captured"}`, inserted ? "Inserted inline" : "Not inserted"));
        header.append(el("span", "card-header-spacer"));
        if (figure) header.append(statusBadge(figure));
        card.append(header);
        const body = el("div", "insertion-body");
        if (inserted) {
            body.append(el("p", "section-label", "Recovered explanation"));
            body.append(el("div", "inserted-text", insertion.insertedText));
            body.append(el("p", "section-label", "Surrounding article context"));
            const context = el("div", "insertion-context");
            context.append(document.createTextNode(insertion.contextBefore || ""));
            const marker = el("strong", "", " [Figure explanation] ");
            context.append(marker, document.createTextNode(insertion.contextAfter || ""));
            body.append(context);
            const footer = el("div", "insertion-footer");
            footer.append(el("span", "insertion-offset", `characters ${insertion.startOffset}–${insertion.endOffset}`));
            const jump = el("button", "action-button", "Show in enriched article");
            jump.type = "button";
            jump.dataset.action = "view-insertion";
            jump.dataset.ordinal = insertion.figureOrdinal;
            footer.append(jump);
            body.append(footer);
        } else {
            const reason = figure ? figure.error || `Vision status: ${figure.status}. The figure placeholder was removed without replacement.` : "No insertion was recorded.";
            body.append(el("div", "output-message", reason));
        }
        card.append(body);
        elements.content.append(card);
    }
}

function scoreParts(score) {
    if (!score) return [];
    return [
        ["caption", score.caption], ["adjacent", score.adjacentCaption], ["wrapper", score.figureWrapper],
        ["junk URL", score.junkUrl], ["alt", score.altText], ["position", score.documentPosition]
    ].filter(([, value]) => value !== 0);
}

function renderDecisions() {
    const decisions = state.run.decisions;
    if (!decisions.length) {
        const captured = state.runs.find(run => run.id === state.run.id)?.capturedDecisions;
        elements.content.append(captured
            ? emptyPanel("No image candidates discovered", "Extraction completed without finding any image candidates in this article.")
            : emptyPanel("Selection decisions were not captured", "This is a legacy run. New runs save every candidate, score contribution, selection, and rejection reason."));
        return;
    }
    const panel = el("div", "decisions-panel");
    const table = el("table", "decisions-table");
    const head = el("thead");
    const headerRow = el("tr");
    ["Position", "Decision", "Score", "Evidence", "Reason"].forEach(label => headerRow.append(el("th", "", label)));
    head.append(headerRow);
    table.append(head);
    const body = el("tbody");
    for (const decision of decisions) {
        const row = el("tr");
        row.append(el("td", "decision-position", decision.position));
        const decisionCell = el("td");
        decisionCell.append(el("span", `status-badge ${decision.decision === "selected" ? "" : "not-captured"}`, decision.decision));
        if (decision.ordinal) decisionCell.append(el("div", "score-parts", `Figure ${decision.ordinal}`));
        row.append(decisionCell);
        const scoreCell = el("td");
        scoreCell.append(el("div", "score-total", decision.score.total.toFixed(2)));
        const parts = el("div", "score-parts");
        scoreParts(decision.score).forEach(([label, value]) => parts.append(el("span", "score-part", `${label} ${value > 0 ? "+" : ""}${value.toFixed(2)}`)));
        scoreCell.append(parts);
        row.append(scoreCell);
        const evidence = el("td", "decision-evidence");
        evidence.append(el("div", "decision-evidence-title", decision.caption || decision.altText || "No caption or useful alt text"));
        if (decision.imageUrl) evidence.append(externalLink(decision.imageUrl, "decision-evidence-url", decision.imageUrl));
        row.append(evidence);
        row.append(el("td", "decision-reason", decision.reason));
        body.append(row);
    }
    table.append(body);
    panel.append(table);
    elements.content.append(panel);
}

function documentPane(title, text, wordCount, kind) {
    const pane = el("section", "document-pane");
    const header = el("header", "document-pane-header");
    header.append(el("span", "", title), el("span", "", `${wordCount.toLocaleString()} words`));
    pane.append(header);
    const scroll = el("div", "document-scroll");
    scroll.dataset.document = kind;
    const content = el("div", "document-text");
    if (kind === "original") appendOriginal(content, text || "");
    else if (kind === "enriched") appendEnriched(content, text || "");
    else content.textContent = text || "Not captured.";
    scroll.append(content);
    pane.append(scroll);
    return pane;
}

function appendOriginal(container, text) {
    const pattern = /\[Figure (\d+)\]/g;
    let cursor = 0;
    for (const match of text.matchAll(pattern)) {
        container.append(document.createTextNode(text.slice(cursor, match.index)));
        container.append(el("mark", "placeholder-mark", match[0]));
        cursor = match.index + match[0].length;
    }
    container.append(document.createTextNode(text.slice(cursor)));
}

function appendEnriched(container, text) {
    const insertions = state.run.insertions.filter(insertion => insertion.startOffset >= 0)
        .sort((left, right) => left.startOffset - right.startOffset);
    let cursor = 0;
    for (const insertion of insertions) {
        if (insertion.startOffset < cursor || insertion.endOffset > text.length) continue;
        container.append(document.createTextNode(text.slice(cursor, insertion.startOffset)));
        const anchor = el("span", "insertion-anchor");
        anchor.id = `insertion-${insertion.figureOrdinal}`;
        anchor.append(el("span", "insertion-label", `Figure ${insertion.figureOrdinal}`));
        anchor.append(el("mark", "", text.slice(insertion.startOffset, insertion.endOffset)));
        container.append(anchor);
        cursor = insertion.endOffset;
    }
    container.append(document.createTextNode(text.slice(cursor)));
}

function renderDocuments() {
    const run = state.run;
    const panel = el("section", "documents-panel");
    const toolbar = el("div", "document-toolbar");
    const switcher = el("div", "document-switch");
    [["compare", "Compare"], ["original", "Original"], ["enriched", "Enriched"], ["summary", "Summary"]].forEach(([value, label]) => {
        const button = el("button", value === state.documentView ? "active" : "", label);
        button.type = "button";
        button.dataset.action = "document-view";
        button.dataset.view = value;
        switcher.append(button);
    });
    toolbar.append(switcher);
    if (state.documentView === "compare") {
        const label = el("label", "sync-control");
        const checkbox = el("input");
        checkbox.type = "checkbox";
        checkbox.checked = state.syncScroll;
        checkbox.dataset.action = "sync-scroll";
        label.append(checkbox, document.createTextNode("Synchronize scroll"));
        toolbar.append(label);
    }
    panel.append(toolbar);
    if (run.reusedFromBookmark) panel.append(el("div", "reuse-banner", `Summary reused from bookmark ${run.reusedFromBookmark}`));
    const grid = el("div", `document-grid${state.documentView === "compare" ? "" : " single"}`);
    if (state.documentView === "compare" || state.documentView === "original") {
        grid.append(documentPane("Original", run.originalArticle, run.originalWordCount, "original"));
    }
    if (state.documentView === "compare" || state.documentView === "enriched") {
        grid.append(documentPane("Enriched", run.enrichedArticle, run.enrichedWordCount, "enriched"));
    }
    if (state.documentView === "summary") grid.append(documentPane("Summary", run.summary, run.summaryWordCount, "summary"));
    panel.append(grid);
    elements.content.append(panel);
    if (state.documentView === "compare") synchronizeDocuments();
}

function synchronizeDocuments() {
    const panes = [...elements.content.querySelectorAll(".document-scroll")];
    if (panes.length !== 2) return;
    let syncing = false;
    panes.forEach((pane, index) => pane.addEventListener("scroll", () => {
        if (!state.syncScroll || syncing) return;
        const other = panes[1 - index];
        const sourceRange = pane.scrollHeight - pane.clientHeight;
        const targetRange = other.scrollHeight - other.clientHeight;
        if (sourceRange <= 0 || targetRange <= 0) return;
        syncing = true;
        other.scrollTop = (pane.scrollTop / sourceRange) * targetRange;
        requestAnimationFrame(() => { syncing = false; });
    }));
}

function showInsertion(ordinal) {
    state.tab = "documents";
    state.documentView = "enriched";
    renderRun();
    requestAnimationFrame(() => {
        const target = document.getElementById(`insertion-${ordinal}`);
        if (!target) {
            showError("No enriched insertion was recorded for this figure.");
            return;
        }
        target.scrollIntoView({block: "center"});
        target.classList.add("highlighted");
        setTimeout(() => target.classList.remove("highlighted"), 1600);
    });
}

elements.list.addEventListener("click", event => {
    const card = event.target.closest("[data-run-id]");
    if (card) selectRun(card.dataset.runId);
});

elements.search.addEventListener("input", event => {
    state.query = event.target.value.trim();
    renderRunList();
});

elements.refresh.addEventListener("click", () => loadRuns(state.selectedId));

document.querySelector(".filter-row").addEventListener("click", event => {
    const button = event.target.closest("[data-filter]");
    if (!button) return;
    state.filter = button.dataset.filter;
    document.querySelectorAll(".filter-button").forEach(item => item.classList.toggle("active", item === button));
    renderRunList();
});

document.getElementById("view-tabs").addEventListener("click", event => {
    const tab = event.target.closest("[data-tab]");
    if (!tab) return;
    state.tab = tab.dataset.tab;
    document.querySelectorAll(".view-tab").forEach(item => item.classList.toggle("active", item === tab));
    renderTab();
});

elements.content.addEventListener("click", event => {
    const action = event.target.closest("[data-action]");
    if (!action) return;
    if (action.dataset.action === "view-insertion") showInsertion(Number(action.dataset.ordinal));
    if (action.dataset.action === "document-view") {
        state.documentView = action.dataset.view;
        clear(elements.content);
        renderDocuments();
    }
});

elements.content.addEventListener("change", event => {
    if (event.target.dataset.action === "sync-scroll") state.syncScroll = event.target.checked;
});

loadRuns();
