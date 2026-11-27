import {
  startAttempt,
  finishAttempt,
  selectAttempt,
  poppedOut,
} from "./flow.js";
import { settings, overview } from "./config.js";
import { requestFor, decode } from "./wire.js";

const $ = (id) => document.getElementById(id);
const names = {
  available: "Find compartments",
  reserve: "Reserve compartment",
  get: "Look up reservation",
  cancel: "Cancel reservation",
  deposit: "Deposit parcel",
  reissue: "Reissue pickup code",
  collect: "Collect parcel",
};
const hints = {
  available: "Read the live directory using the selected protocol.",
  reserve: "Choose a free compartment and reserve it as the customer.",
  get: "Read the reservation using its ID.",
  cancel: "The owner can cancel a reservation before deposit.",
  deposit: "Switch to Courier to deposit the parcel.",
  reissue:
    "As the customer, request a fresh pickup code. The previous code expires.",
  collect: "Switch to Customer and enter the code from the pickup email.",
};
const preferred = { reserve: "REST", deposit: "SOAP", collect: "GraphQL" };
const read = (op) => op === "get" || op === "available";
let slots = [],
  selected = "",
  lastCommand = null,
  busy = false;
const role = () => document.querySelector('[name="role"]:checked').value;

function configure(op, changeProtocol = false) {
  $("operation").value = op;
  if (changeProtocol && preferred[op]) $("protocol").value = preferred[op];
  $("slots-panel").hidden = op !== "reserve" && op !== "available";
  $("booking-label").hidden = op === "reserve" || op === "available";
  $("code-label").hidden = op !== "collect";
  $("execute").textContent = names[op];
  $("hint").textContent = hints[op];
  document
    .querySelectorAll("[data-step]")
    .forEach((button) =>
      button.setAttribute("aria-current", String(button.dataset.step === op)),
    );
}

function showSlots(items) {
  slots = items;
  if (!slots.some((s) => s.id === selected)) selected = slots[0]?.id || "";
  $("slots").replaceChildren();
  for (const slot of slots) {
    const button = document.createElement("button");
    button.textContent = slot.id;
    button.title = slot.site + " · " + slot.size;
    button.setAttribute("aria-pressed", String(slot.id === selected));
    button.onclick = () => {
      selected = slot.id;
      showSlots(slots);
    };
    $("slots").append(button);
  }
  if (!items.length) $("slots").textContent = "No compartments available.";
}

function showReservation(res) {
  $("state").textContent = res.state;
  $("current-id").textContent = res.id;
  $("current-slot").textContent = res.compartmentId;
  $("booking").value = res.id;
  $("view-logs").href =
    overview + "?var-reservation=" +
    encodeURIComponent(res.id);
  $("view-logs").hidden = false;
}

function feedback(text, error = false) {
  $("feedback").textContent = text;
  $("feedback").classList.toggle("bad", error);
}

function addHistory(command, wire, response, status, outcome, elapsed, traceId) {
  const item = document.createElement("details"),
    title = document.createElement("summary"),
    detail = document.createElement("pre");
  const safe = (text) =>
    command.code ? text.replaceAll(command.code, "[masked]") : text;
  const http = status === "Network error" ? status : `HTTP ${status}`;
  title.textContent = [command.protocol, command.op, outcome, http, `${elapsed} ms`]
    .filter(Boolean).join(" · ");
  detail.textContent = safe(
    [
      wire.method + " " + wire.path,
      "Role: " + command.role,
      ...Object.entries(wire.headers).map(([k, v]) => k + ": " + v),
      wire.body || "",
      "",
      "Response",
      response,
    ].join("\n"),
  );
  const follow = document.createElement("button");
  follow.textContent = "Inspect request flow";
  follow.onclick = () => {
    selectAttempt(traceId);
    $("flow-panel").scrollIntoView();
  };
  item.append(title, detail, follow);
  const entries = $("entries");
  entries.prepend(item);
  if (entries.children.length > settings.historyLimit) entries.lastChild.remove();
  $("count").textContent =
    entries.children.length +
    " request" +
    (entries.children.length === 1 ? "" : "s");
}

async function execute(command, retry = false) {
  if (busy) return;
  if (command.op !== "available" && !command.id)
    return feedback("Choose a compartment or enter a reservation ID.", true);
  if (command.op === "collect" && !/^\d{8}$/.test(command.code))
    return feedback("Enter the eight-digit pickup code.", true);
  busy = true;
  $("execute").disabled = $("refresh").disabled = $("retry").disabled = true;
  if (!read(command.op)) lastCommand = command;
  const wire = requestFor(command);
  const attempt = startAttempt(command.protocol, command.op, retry);
  const started = performance.now();
  let success = false;
  let outcome = "";
  let status = "Network error",
    raw = "";
  try {
    const reply = await fetch(
      "/gateway/" + command.protocol.toLowerCase() + wire.path,
      {
        method: wire.method,
        headers: {
          ...wire.headers,
          "X-Lab-Role": command.role,
          traceparent: attempt.parent,
        },
        body: wire.body,
      },
    );
    status = String(reply.status);
    raw = await reply.text();
    const value = decode(command.protocol, command.op, reply.ok, raw);
    success = true;
    if (command.op === "available") showSlots(value);
    else {
      showReservation(value);
      if (!retry) {
        if (command.op === "reserve") {
          showSlots(slots.filter((s) => s.id !== value.compartmentId));
          configure("deposit", true);
        }
        if (command.op === "deposit" || command.op === "reissue")
          configure("collect", true);
        if (command.op === "collect" || command.op === "cancel") {
          $("pickup").value = "";
          configure("get");
        }
      }
    }
    feedback(
      retry
        ? "Retry returned the original result."
        : command.op === "available"
          ? value.length + " compartments available."
          : "Reservation is " + value.state.toLowerCase() + ".",
    );
  } catch (error) {
    outcome = error.code || "Failed";
    feedback(error.message, true);
  } finally {
    finishAttempt(attempt.id, status, success);
    addHistory(
      command,
      wire,
      raw,
      status,
      outcome,
      Math.round(performance.now() - started),
      attempt.id,
    );
    busy = false;
    $("execute").disabled = $("refresh").disabled = false;
    $("retry").disabled = !lastCommand;
  }
}
function command(op) {
  return {
    op,
    protocol: $("protocol").value,
    role: role(),
    id: op === "reserve" ? selected : $("booking").value.trim(),
    key: read(op) ? "" : crypto.randomUUID(),
    code: op === "collect" ? $("pickup").value : "",
  };
}
$("operation").onchange = () => configure($("operation").value, true);
document
  .querySelectorAll("[data-step]")
  .forEach(
    (button) => (button.onclick = () => configure(button.dataset.step, true)),
  );
$("refresh").onclick = () => execute(command("available"));
$("execute").onclick = () => execute(command($("operation").value));
$("retry").onclick = () => lastCommand && execute(lastCommand, true);
if (!poppedOut) {
  configure("reserve", true);
  execute(command("available"));
}
