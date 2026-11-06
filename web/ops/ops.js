/**
 * Fleet Pulse — a page that reads /health/kingdom and says what is wrong.
 *
 * The endpoint already exists and is public, so this page needs no token and no build step.
 * It exists because 27 agents were producing telemetry that nobody was reading.
 */
(function () {
  "use strict";

  const DEFAULT_BASE = "https://indoverification-production.up.railway.app";
  const POLL_MS = 30000;

  const params = new URLSearchParams(window.location.search);
  const API_BASE = (params.get("api") || DEFAULT_BASE).replace(/\/+$/, "");

  const el = {
    banner: document.getElementById("banner"),
    bannerStatus: document.getElementById("banner-status"),
    bannerSummary: document.getElementById("banner-summary"),
    pillars: document.getElementById("pillars"),
    chips: document.getElementById("chips"),
    raw: document.getElementById("raw"),
    updated: document.getElementById("updated"),
    refresh: document.getElementById("refresh"),
    endpoint: document.getElementById("endpoint"),
  };

  const STATUS_CLASS = {
    HEALTHY: "healthy",
    DEGRADED: "degraded",
    CRITICAL: "critical",
  };

  const PILLAR_LABEL = {
    resilience: "Provider circuit",
    host: "Host memory & disk",
    persistence: "Database pool",
    omnichannel: "WhatsApp quota",
    financials: "Cost ledger",
  };

  function classFor(status) {
    return STATUS_CLASS[String(status || "").toUpperCase()] || "unreachable";
  }

  function render(snapshot) {
    const overall = String(snapshot.status || "UNKNOWN").toUpperCase();
    const tone = STATUS_CLASS[overall] || "unknown";

    el.banner.className = "banner banner--" + tone;
    el.bannerStatus.textContent = overall;
    el.bannerSummary.textContent = snapshot.summary || "";

    const subsystems = snapshot.subsystems || {};
    el.pillars.replaceChildren(
      ...Object.entries(subsystems).map(function (entry) {
        const name = entry[0];
        const report = entry[1] || {};
        const card = document.createElement("div");
        card.className = "card card--" + classFor(report.status);

        const title = document.createElement("h3");
        title.textContent = PILLAR_LABEL[name] || name;

        const line = document.createElement("p");
        const bits = [String(report.status || "")];
        if (report.error) bits.push(String(report.error));
        if (report.details && Object.keys(report.details).length) {
          bits.push(
            Object.entries(report.details)
              .map(function (pair) {
                return pair[0] + "=" + pair[1];
              })
              .join(" · ")
          );
        }
        line.textContent = bits.join(" — ");

        card.append(title, line);
        return card;
      })
    );

    const chips = Array.isArray(snapshot.action_chips) ? snapshot.action_chips : [];
    el.chips.replaceChildren(
      ...chips.map(function (chip) {
        const node = document.createElement("span");
        node.className = "chip";
        node.textContent = chip.label || chip.action || "action";
        return node;
      })
    );

    el.raw.textContent = JSON.stringify(snapshot, null, 2);
    el.updated.textContent = new Date().toLocaleTimeString();
  }

  function renderError(error) {
    el.banner.className = "banner banner--critical";
    el.bannerStatus.textContent = "UNREACHABLE";
    el.bannerSummary.textContent = String(error && error.message ? error.message : error);
    el.updated.textContent = new Date().toLocaleTimeString();
  }

  async function load() {
    try {
      const response = await fetch(API_BASE + "/health/kingdom", {
        headers: { accept: "application/json" },
        cache: "no-store",
      });

      if (!response.ok) {
        throw new Error("HTTP " + response.status);
      }

      render(await response.json());
    } catch (error) {
      renderError(error);
    }
  }

  el.endpoint.textContent = API_BASE + "/health/kingdom";
  el.refresh.addEventListener("click", load);

  load();
  window.setInterval(load, POLL_MS);
})();
