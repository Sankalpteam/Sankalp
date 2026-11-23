/* Wallet screen.
 *
 * Reads console/<uid>/wallet and fills the balance, the runway note and the ledger.
 * Nothing here writes: money is credited by the gateway when a payment settles, and
 * debited as each request is metered. A browser that could write a balance would be a
 * browser that could give itself credit.
 *
 * Requires, in order: firebase compat SDK, firebase-config.js, ../shared/format.js,
 * ../shared/api.js.
 */
(function () {
  'use strict';

  const F = window.SankalpFormat;
  const API = window.SankalpConsole;
  const balanceNode = document.querySelector('.mainhead b, .bal b, .balance b');
  const runwayNode = balanceNode ? balanceNode.parentElement.querySelector('small') : null;
  const ledgerRows = document.querySelector('.main table tbody');
  if (!API) return;

  let unsubscribe = null;

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }

  /**
   * How long the balance lasts at this month's rate. Returns '' when there is no spending
   * to divide by — a made-up number is worse than no number.
   */
  function runway(data, balancePaise) {
    const usage = API.usageFor(data, F.currentMonthKey());
    const now = new Date();
    const dayOfMonth = now.getDate();
    if (!usage.spentPaise || dayOfMonth < 1) return '';
    const perDay = usage.spentPaise / dayOfMonth;
    if (perDay <= 0) return '';
    const days = Math.floor(balancePaise / perDay);
    if (days <= 0) return 'no credit left at this rate';
    if (days > 365) return 'more than a year at this rate';
    return `about ${days} ${days === 1 ? 'day' : 'days'} left at this rate`;
  }

  function paint(data) {
    const wallet = (data && data.wallet) || { balancePaise: 0, ledger: {} };
    const balance = Number(wallet.balancePaise) || 0;

    if (balanceNode) balanceNode.textContent = F.formatPaise(balance);
    if (runwayNode) {
      const note = runway(data, balance);
      runwayNode.textContent = note || 'no spending yet this month';
    }

    if (!ledgerRows) return;
    const entries = API.ledgerList(data);
    ledgerRows.innerHTML = entries.length
      ? entries.map((entry) => {
          const amount = Number(entry.amountPaise) || 0;
          const sign = amount >= 0 ? '+' : '\u2212';
          const amountClass = amount >= 0 ? 'right mono ok' : 'right mono';
          return `<tr>
            <td class="mini">${escapeHtml(F.formatWhen(entry.at))}</td>
            <td>${escapeHtml(entry.note || entry.kind || '')}</td>
            <td class="${amountClass}">${sign}${escapeHtml(F.formatPaise(Math.abs(amount)))}</td>
            <td class="right mono">${escapeHtml(F.formatPaise(entry.balanceAfterPaise))}</td>
          </tr>`;
        }).join('')
      : '<tr><td colspan="4" class="mini">Nothing has moved yet. Top up to start.</td></tr>';
  }

  function say(message, kind) {
    const note = document.getElementById('wallet-note');
    if (!note) return;
    note.textContent = message || '';
    note.className = `note ${kind || ''}`.trim();
  }

  API.onSignedIn((user) => {
    if (!user) {
      paint(API.EMPTY);
      say('Sign in to see your wallet.', 'warn');
      return;
    }
    say('');
    if (unsubscribe) unsubscribe();
    unsubscribe = API.watchAll(paint, (error) => say(`Could not read the wallet: ${error.message}`, 'warn'));
  });

  window.addEventListener('pagehide', () => {
    if (unsubscribe) unsubscribe();
  });
})();
