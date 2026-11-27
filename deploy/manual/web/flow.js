import { settings } from "./config.js";

const $ = (id) => document.getElementById(id);
const prefix = "locker.flow.";
const hexId = () => crypto.randomUUID().replaceAll("-", "");
export const poppedOut = location.pathname === "/flow";
const journey = poppedOut
  ? location.hash.slice(1)
  : (sessionStorage.lockerJourney ||= hexId());
const key = prefix + journey;
function retained(name) {
  const cutoff = Date.now() - settings.retentionHours * 3600000;
  return JSON.parse(localStorage.getItem(name) || "[]")
    .filter((attempt) => attempt.at >= cutoff)
    .slice(0, settings.historyLimit);
}
const list = () => retained(key);
const current = () =>
  list().find((a) => a.id === $("flow-attempt").value) || list()[0];
const clock = (ms) => new Date(ms).toLocaleTimeString([], { hour12: false });
const page = { rows: 0, changes: 0 };
const cache = {};
let selectedStep = "";
let timeline = [];
let refreshing = false;
let refreshAgain = false;

for (const name of Object.keys(localStorage).filter((s) =>
  s.startsWith(prefix),
)) {
  const attempts = retained(name);
  if (attempts.length) localStorage.setItem(name, JSON.stringify(attempts));
  else localStorage.removeItem(name);
}
$("flow-open").href = "/flow#" + journey;
if (poppedOut) {
  document.body.classList.add("flow-window");
  document.title = "Live request flow · Locker Lab";
}

function label(a) {
  return a.protocol + " · " + a.operation + (a.retry ? " · retry" : "");
}
function update(attempt) {
  const attempts = list();
  const index = attempts.findIndex((a) => a.id === attempt.id);
  if (index < 0) attempts.unshift(attempt);
  else attempts[index] = attempt;
  localStorage.setItem(key, JSON.stringify(attempts.slice(0, settings.historyLimit)));
  sync();
}
export function startAttempt(protocol, operation, retry) {
  const id = hexId();
  update({ id, protocol, operation, retry, at: Date.now() });
  return { id, parent: "00-" + id + "-" + hexId().slice(0, 16) + "-01" };
}
export function finishAttempt(id, status, success) {
  const a = list().find((item) => item.id === id);
  if (a) update({ ...a, status, success, end: Date.now() });
}
export function selectAttempt(id) {
  $("flow-attempt").value = id;
  sync();
}
function sync() {
  const select = $("flow-attempt"),
    pinned = select.value;
  select.replaceChildren(new Option("Follow latest request", ""));
  for (const a of list())
    select.add(new Option(clock(a.at) + "  " + label(a), a.id));
  select.value = [...select.options].some((o) => o.value === pinned)
    ? pinned
    : "";
  const a = current();
  $("flow-summary").textContent = a
    ? label(a) +
      " · " +
      (a.end
        ? a.success
          ? "Response received"
          : "Request failed"
        : "In progress")
    : "Waiting for a request";
  if ($("flow-panel").dataset.trace !== (a?.id || "")) {
    $("flow-panel").dataset.trace = a?.id || "";
    selectedStep = "";
    page.changes = 0;
    timeline = [];
    delete cache.trace;
    paintTimeline(a);
    $("db-changes").replaceChildren();
    delete cache.changes;
  }
  if (a?.end) paintTimeline(a);
  refresh();
}
window.addEventListener("storage", (e) => {
  if (e.key === key) sync();
});
$("flow-attempt").onchange = sync;

async function get(path) {
  const response = await fetch(path, { signal: AbortSignal.timeout(settings.requestTimeoutMs) });
  if (!response.ok) throw new Error("Source unavailable");
  return response.json();
}
function el(tag, content) {
  const element = document.createElement(tag);
  if (content !== undefined) element.textContent = content;
  return element;
}
async function loadDiagram() {
  const response = await fetch("/diagram.svg", { signal: AbortSignal.timeout(settings.requestTimeoutMs) });
  if (!response.ok) throw new Error("Diagram unavailable");
  const svg = new DOMParser().parseFromString(
    await response.text(),
    "image/svg+xml",
  ).documentElement;
  svg.removeAttribute("width");
  svg.removeAttribute("height");
  svg.setAttribute("role", "img");
  svg.setAttribute("aria-label", "Live locker architecture");
  $("flow-diagram").replaceChildren(document.importNode(svg, true));
  paintDiagram();
}
function paintDiagram() {
  const nodes = new Set(),
    edges = new Set(),
    failures = new Set();
  const attempt = current();
  if (attempt?.end && /^\d{3}$/.test(attempt.status)) {
    const gateway = attempt.protocol.toLowerCase();
    nodes.add("browser");
    nodes.add(gateway);
    edges.add("browser--" + gateway);
    if (!attempt.success) failures.add("browser--" + gateway);
  }
  for (const step of timeline) {
    nodes.add(step.source);
    nodes.add(step.target);
    const edge = step.source + "--" + step.target;
    edges.add(edge);
    if (step.failed) failures.add(edge);
  }
  const focus = timeline.find((s) => s.id === selectedStep);
  for (const node of $("flow-diagram").querySelectorAll(".node")) {
    const name = node.querySelector("title").textContent;
    node.classList.toggle("seen", nodes.has(name));
    node.classList.toggle(
      "focused",
      name === focus?.source || name === focus?.target,
    );
  }
  for (const edge of $("flow-diagram").querySelectorAll(".edge")) {
    edge.classList.toggle("seen", edges.has(edge.id));
    edge.classList.toggle("failed", failures.has(edge.id));
    edge.classList.toggle(
      "focused",
      edge.id === focus?.source + "--" + focus?.target,
    );
  }
}
function stepInfo(steps) {
  const step = steps.find((s) => s.id === selectedStep);
  $("step-detail").textContent = step
    ? step.source +
      " → " +
      step.target +
      " · " +
      step.operation +
      " · " +
      clock(step.at) +
      " · " +
      step.ms +
      " ms · " +
      (step.failed ? "Failed" : "Observed")
    : "Select a step to inspect its timing.";
  paintDiagram();
}
function paintTimeline(a) {
  const box = $("flow-timeline");
  const steps = [...timeline];
  if (a?.end)
    steps.push({
      id: "response",
      operation: a.success ? "HTTP response received" : "Request failed",
      source: a.protocol.toLowerCase(),
      target: "browser",
      at: a.end,
      ms: a.end - a.at,
      failed: !a.success,
    });
  steps.sort((s, t) => s.at - t.at);
  box.replaceChildren(
    ...steps.map((step) => {
      const button = el("button");
      button.type = "button";
      button.dataset.stepId = step.id;
      button.className = step.failed ? "bad-step" : "";
      button.setAttribute("aria-pressed", String(selectedStep === step.id));
      button.append(
        el("time", clock(step.at)),
        el("strong", step.operation),
        el("small", step.source + " → " + step.target),
      );
      button.onclick = () => {
        selectedStep = step.id;
        for (const b of box.children)
          b.setAttribute("aria-pressed", String(b === button));
        stepInfo(steps);
      };
      return button;
    }),
  );
  if (!steps.length)
    box.append(
      el(
        "p",
        a ? "Waiting for trace data…" : "Run an operation to see its steps.",
      ),
    );
  stepInfo(steps);
}
function drawRows(data) {
  const table = el("table"),
    header = table.createTHead().insertRow(),
    body = table.createTBody();
  for (const col of data.columns) header.append(el("th", col));
  for (const row of data.rows) {
    const tr = body.insertRow();
    for (const col of data.columns)
      tr.insertCell().textContent = row[col] ?? "—";
  }
  $("db-rows").replaceChildren(table);
  if (!data.rows.length) $("db-rows").append(el("p", "No rows on this page."));
}
function drawChanges(data) {
  const nodes = data.rows.map((change) => {
    const detail = el("details"),
      title = el("summary"),
      values = change.after_row || change.before_row;
    title.append(
      el("time", clock(new Date(change.at))),
      el("b", change.action),
      el("span", change.table_name),
    );
    if (values.state) title.append(el("small", values.state));
    detail.append(title);
    for (const [caption, row] of [
      ["Before", change.before_row],
      ["After", change.after_row],
    ]) {
      if (row)
        detail.append(
          el("strong", caption),
          el("pre", JSON.stringify(row, null, 2)),
        );
    }
    return detail;
  });
  $("db-changes").replaceChildren(
    ...(nodes.length ? nodes : [el("p", "No committed changes.")]),
  );
}
async function panel(kind, url, stillCurrent, render) {
  const status = $(kind + "-status");
  try {
    const data = await get(url);
    if (!stillCurrent()) return;
    const signature = url + JSON.stringify(data);
    if (cache[kind] !== signature) {
      render(data);
      cache[kind] = signature;
    }
    if (kind !== "trace") {
      $(kind + "-prev").disabled = page[kind] === 0;
      $(kind + "-next").disabled = !data.more;
      $(kind + "-page").textContent = "Page " + (page[kind] + 1);
    }
    status.textContent = data.waiting
      ? "Awaiting spans"
      : "Updated " + clock(Date.now());
  } catch {
    if (stillCurrent()) status.textContent = "Source unavailable · retrying";
  }
}
async function refresh() {
  if (document.hidden) return;
  if (refreshing) {
    refreshAgain = true;
    return;
  }
  refreshing = true;
  try {
    const a = current(),
      table = $("db-table").value,
      scope = $("change-scope").value;
    const rowPage = page.rows,
      changePage = page.changes;
    const work = [
      panel(
        "rows",
        "/inspect/tables/" + table + "?page=" + rowPage,
        () => table === $("db-table").value && rowPage === page.rows,
        drawRows,
      ),
    ];
    if (a)
      work.push(
        panel(
          "trace",
          "/inspect/traces/" + a.id,
          () => current()?.id === a.id,
          (data) => {
            const observed = new Map(timeline.map((s) => [s.id, s]));
            for (const step of data.steps) observed.set(step.id, step);
            timeline = Array.from(observed.values());
            paintTimeline(current());
          },
        ),
      );
    if (scope === "all" || a)
      work.push(
        panel(
          "changes",
          "/inspect/changes?page=" +
            changePage +
            (scope === "all" ? "" : "&trace=" + a.id),
          () =>
            scope === $("change-scope").value &&
            changePage === page.changes &&
            (scope === "all" || current()?.id === a?.id),
          drawChanges,
        ),
      );
    await Promise.all(work);
  } finally {
    refreshing = false;
    if (refreshAgain) {
      refreshAgain = false;
      setTimeout(refresh, 0);
    }
  }
}
for (const kind of ["rows", "changes"]) {
  for (const [direction, delta] of [
    ["prev", -1],
    ["next", 1],
  ]) {
    $(kind + "-" + direction).onclick = () => {
      $(kind + "-next").disabled = true;
      page[kind] = Math.max(0, page[kind] + delta);
      refresh();
    };
  }
}
$("db-table").onchange = () => {
  page.rows = 0;
  refresh();
};
$("change-scope").onchange = () => {
  page.changes = 0;
  refresh();
};
loadDiagram().catch(() => {
  $("flow-diagram").textContent = "Architecture diagram unavailable";
});
sync();
document.addEventListener("visibilitychange", sync);
setInterval(() => {
  if (!refreshing) refresh();
}, settings.refreshMs);
