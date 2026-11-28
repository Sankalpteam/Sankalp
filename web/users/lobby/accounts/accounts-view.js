/**
 * The accounts list the app calls "Your accounts".
 *
 * Everything around this was already here and only the drawing was missing: the section is in
 * index.html (#content), style.css carries the card, the icon tile, the code, the timer ring and
 * the star, IndooneCloudAccounts fills window.indooneState.accounts, and IndooneTotpController
 * keeps account.code and account.seconds up to date. Both home.js and the search screen call
 * renderAccounts() and refreshAccountCodes(), and IndooneTotpController.refreshAll() calls
 * renderAccounts() once a second - none of which existed, so the calls either did nothing or
 * threw.
 *
 * This draws into the markup that is already there rather than adding a second screen.
 */
(() => {
  function el(id) {
    return document.getElementById(id);
  }

  function accounts() {
    return Array.isArray(window.indooneState?.accounts)
      ? window.indooneState.accounts
      : [];
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[character]));
  }

  function visibleAccounts() {
    const search = String(window.indooneState?.search || '').toLowerCase().trim();
    const list = accounts().slice();

    // Favourites first, then by name - the app's own order (AccountsScreen.kt).
    list.sort((a, b) => {
      if (Boolean(b.favorite) !== Boolean(a.favorite)) return b.favorite ? 1 : -1;
      return String(a.name || '').localeCompare(String(b.name || ''));
    });

    if (!search) return list;

    return list.filter(account =>
      `${account.name || ''} ${account.email || ''}`.toLowerCase().includes(search)
    );
  }

  // The three states the app uses, at the same thresholds: 15 seconds left is plenty, 5 is
  // running out, anything less is about to roll over.
  function toneClass(seconds) {
    if (seconds >= 15) return 'timer-green';
    if (seconds >= 5) return 'timer-yellow';
    return 'timer-red';
  }

  function card(account) {
    const seconds = Number.isFinite(Number(account.seconds))
      ? Number(account.seconds)
      : 0;
    const service = String(account.cls || 'google');
    const icon = String(account.icon || account.name || '?').charAt(0).toUpperCase();
    const code = String(account.code || '');

    return `
      <div
        class="account"
        role="button"
        tabindex="0"
        data-account-id="${escapeHtml(account.id)}"
        aria-label="${escapeHtml(account.name)} ${escapeHtml(code)}"
      >
        <div class="service-icon ${escapeHtml(service)}" aria-hidden="true">${escapeHtml(icon)}</div>
        <div class="account-info">
          <b>${escapeHtml(account.name || 'Account')}</b>
          <span>${escapeHtml(account.email || '')}</span>
          <strong>${escapeHtml(code)}</strong>
        </div>
        <button
          type="button"
          class="fav ${account.favorite ? 'active' : ''}"
          data-favourite-id="${escapeHtml(account.id)}"
          aria-label="${account.favorite ? 'Remove from favourites' : 'Add to favourites'}"
        >${account.favorite ? '★' : '☆'}</button>
        <div class="timer ${toneClass(seconds)}" aria-hidden="true">${seconds}</div>
      </div>
    `;
  }

  function renderAccounts() {
    const list = el('accountList');
    const empty = el('emptyState');

    if (!list) return;

    const visible = visibleAccounts();

    if (!visible.length) {
      list.innerHTML = '';
      empty?.removeAttribute('hidden');
      return;
    }

    empty?.setAttribute('hidden', '');
    list.innerHTML = visible.map(card).join('');
  }

  async function refreshAccountCodes() {
    const controller = window.IndooneTotpController;

    if (!controller?.refreshAccount) return;

    await Promise.all(accounts().map(account => controller.refreshAccount(account)));
    renderAccounts();
  }

  function openAccount(id) {
    const account = accounts().find(item => String(item.id) === String(id));

    if (!account) return;

    window.indooneState.activeAccount = account;
    window.openMenuNested?.('accounts/edit');
  }

  async function toggleFavourite(id) {
    const account = accounts().find(item => String(item.id) === String(id));

    if (!account) return;

    const before = Boolean(account.favorite);
    account.favorite = !before;

    try {
      await window.IndooneCloudAccounts?.save?.(account);
    } catch (error) {
      account.favorite = before;
      console.warn('Sankalp: favourite could not be saved', error);
    }

    renderAccounts();
  }

  function bind() {
    const list = el('accountList');

    if (!list || list.dataset.bound === 'true') return;

    list.dataset.bound = 'true';

    list.addEventListener('click', event => {
      const star = event.target.closest('[data-favourite-id]');

      if (star) {
        event.preventDefault();
        event.stopPropagation();
        void toggleFavourite(star.dataset.favouriteId);
        return;
      }

      const row = event.target.closest('[data-account-id]');

      if (row) openAccount(row.dataset.accountId);
    });

    list.addEventListener('keydown', event => {
      if (event.key !== 'Enter' && event.key !== ' ') return;

      const star = event.target.closest('[data-favourite-id]');

      if (star) {
        event.preventDefault();
        void toggleFavourite(star.dataset.favouriteId);
        return;
      }

      const row = event.target.closest('[data-account-id]');

      if (row) {
        event.preventDefault();
        openAccount(row.dataset.accountId);
      }
    });

    el('emptyAddBtn')?.addEventListener('click', () => {
      window.IndooneHome?.showAddAccount?.();
    });
  }

  /**
   * Show the accounts screen.
   *
   * The screen has been in index.html all along and nothing ever put it on display: the Lobby
   * tab called closeMenuPage, which returns to Home, and the brand button - labelled "Go to
   * accounts" - did the same.
   */
  function showAccounts() {
    window.closeMenuPage?.();

    const home = el('homeContent');
    const accountsView = el('content');

    if (!accountsView) return;

    home?.setAttribute('hidden', '');
    accountsView.removeAttribute('hidden');

    el('addBtn')?.removeAttribute('hidden');
    el('searchWrap')?.removeAttribute('hidden');

    ['homeNav', 'lobbyNav', 'settingsNav'].forEach(id => {
      el(id)?.classList.toggle('active', id === 'lobbyNav');
    });

    bind();
    renderAccounts();
    void refreshAccountCodes();
    window.IndooneTotpController?.start?.();
  }

  window.renderAccounts = renderAccounts;
  window.refreshAccountCodes = refreshAccountCodes;
  window.showAccounts = showAccounts;

  window.IndooneAccountsView = { render: renderAccounts, refresh: refreshAccountCodes, show: showAccounts };
})();
