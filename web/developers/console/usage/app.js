/* Usage screen.
 *
 * Reads console/<uid>/usage/<month> and fills the four KPI blocks and the per-model rows.
 * Nothing here writes: usage is written by the gateway as each request is settled.
 *
 * Requires, in order: firebase compat SDK, firebase-config.js, ../shared/format.js,
 * ../shared/api.js.
 */
(function () {
  'use strict';

  const F = window.SankalpFormat;
  const API = window.SankalpConsole;
  const kpis = document.querySelectorAll('.kpis .kpi .v');
  const modelRows = document.querySelector('.main table tbody');
  const subHeadings = document.querySelectorAll('.sub');
  if (!API || !kpis.length) return;

  let monthKey = F.currentMonthKey();
  let unsubscribe = null;

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }

  function paint(data) {
    const usage = API.usageFor(data, monthKey);

    // The four blocks, in the order the page draws them: requests, input, output, spent.
    kpis[0].textContent = F.formatCount(usage.requests);
    kpis[1].textContent = F.formatTokens(usage.inputTokens);
    kpis[2].textContent = F.formatTokens(usage.outputTokens);
    kpis[3].textContent = F.formatPaise(usage.spentPaise);

    const aliases = Object.keys(usage.byAlias || {})
      .map((name) => Object.assign({ name }, usage.byAlias[name]))
      .sort((a, b) => (Number(b.spentPaise) || 0) - (Number(a.spentPaise) || 0));

    const total = aliases.reduce((sum, a) => sum + (Number(a.spentPaise) || 0), 0);

    if (modelRows) {
      modelRows.innerHTML = aliases.length
        ? aliases.map((a) => {
            const share = total > 0 ? Math.round(((Number(a.spentPaise) || 0) / total) * 100) : 0;
            return `<tr>
              <td class="mono">${escapeHtml(a.name)}</td>
              <td class="right">${share}%</td>
              <td class="right mono">${escapeHtml(F.formatTokens(a.inputTokens))}</td>
              <td class="right mono">${escapeHtml(F.formatTokens(a.outputTokens))}</td>
              <td class="right mono">${escapeHtml(F.formatPaise(a.spentPaise))}</td>
            </tr>`;
          }).join('')
        : '<tr><td colspan="5" class="mini">No usage recorded yet this month.</td></tr>';
    }

    if (subHeadings.length) {
      subHeadings[0].textContent = `Spend by model · ${F.formatMonth(monthKey)}`;
    }
  }

  function say(message, kind) {
    const note = document.getElementById('usage-note');
    if (!note) return;
    note.textContent = message || '';
    note.className = `note ${kind || ''}`.trim();
  }

  function subscribe() {
    if (unsubscribe) unsubscribe();
    unsubscribe = API.watchAll(paint, (error) => say(`Could not read usage: ${error.message}`, 'warn'));
  }

  API.onSignedIn((user) => {
    if (!user) {
      paint(API.EMPTY);
      say('Sign in to see usage.', 'warn');
      return;
    }
    say('');
    subscribe();
  });

  window.addEventListener('pagehide', () => {
    if (unsubscribe) unsubscribe();
  });
})();
