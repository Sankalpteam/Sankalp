/**
 * Your accounts - the list the app has had all along and the web never did.
 *
 * The app's AccountsScreen.kt is the reference: the same card, the same code colour by how much
 * of the period is left, the same ring, the same empty state. The data comes from the module
 * that was already here - IndooneCloudAccounts fills window.indooneState.accounts - and the codes
 * come from the TOTP module that the add-account screens already use.
 */
(() => {
  let timer = null;
  let accounts = [];
  let query = '';
  let sortMode = 'favourite';

  const el = id => document.getElementById(id);

  function periodOf(account) {
    const period = Number(account.period) || 30;
    return period > 0 ? period : 30;
  }

  function secondsRemaining(account) {
    const period = periodOf(account);
    const now = Math.floor(Date.now() / 1000);
    return period - (now % period);
  }

  function toneFor(seconds) {
    if (seconds >= 15) return 'is-fresh';
    if (seconds >= 5) return 'is-soon';
    return 'is-urgent';
  }

  async function codeFor(account) {
    const period = periodOf(account);
    const counter = Math.floor(Date.now() / 1000 / period);

    try {
      return await window.TOTP.generate(
        account.secret,
        counter,
        Number(account.digits) || 6,
        account.algorithm || 'SHA1'
      );
    } catch (error) {
      // A secret that cannot be read is shown as dashes rather than as a wrong code: a number
      // that looks right and is not is worse than an obvious gap.
      return '—'.repeat(Number(account.digits) || 6);
    }
  }

  function matches(account) {
    if (!query) return true;

    const haystack = `${account.name || ''} ${account.email || ''}`.toLowerCase();
    return haystack.includes(query);
  }

  function ordered(list) {
    const copy = list.slice();

    if (sortMode === 'favourite') {
      copy.sort((a, b) => {
        if (Boolean(b.favorite) !== Boolean(a.favorite)) return b.favorite ? 1 : -1;
        return String(a.name || '').localeCompare(String(b.name || ''));
      });
    } else {
      copy.sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
    }

    return copy;
  }

  function ring(seconds, period, tone) {
    const progress = Math.max(0, Math.min(1, seconds / period));
    const radius = 15.5;
    const circumference = 2 * Math.PI * radius;

    return `
      <div class="account-ring ${tone}" aria-hidden="true">
        <svg viewBox="0 0 35 35">
          <circle class="ring-track" cx="17.5" cy="17.5" r="${radius}"></circle>
          <circle
            class="ring-arc"
            cx="17.5"
            cy="17.5"
            r="${radius}"
            stroke-dasharray="${circumference}"
            stroke-dashoffset="${circumference * (1 - progress)}"
          ></circle>
        </svg>
        <span class="ring-count">${seconds}</span>
      </div>
    `;
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[character]));
  }

  async function paint() {
    const list = el('accountsList');
    const empty = el('accountsEmpty');

    if (!list) return;

    const visible = ordered(accounts.filter(matches));

    if (!visible.length) {
      list.innerHTML = '';
      empty?.removeAttribute('hidden');
      return;
    }

    empty?.setAttribute('hidden', '');

    const rows = await Promise.all(visible.map(async account => {
      const seconds = secondsRemaining(account);
      const tone = toneFor(seconds);
      const code = await codeFor(account);
      const icon = String(account.icon || account.name || '?').charAt(0).toUpperCase();

      return `
        <button
          type="button"
          class="account-card"
          data-account-id="${escapeHtml(account.id)}"
          aria-label="${escapeHtml(account.name)} ${escapeHtml(code)}"
        >
          <span class="account-icon" aria-hidden="true">${escapeHtml(icon)}</span>
          <span class="account-body">
            <span class="account-name">${escapeHtml(account.name || 'Account')}</span>
            <span class="account-email">${escapeHtml(account.email || '')}</span>
            <span class="account-code ${tone}">${escapeHtml(String(code).replace(/(\d{3})(?=\d)/g, '$1 '))}</span>
          </span>
          <span
            class="account-star ${account.favorite ? 'is-on' : ''}"
            role="button"
            tabindex="0"
            data-favourite-id="${escapeHtml(account.id)}"
            aria-label="${account.favorite ? 'Remove from favourites' : 'Add to favourites'}"
          >${account.favorite ? '★' : '☆'}</span>
          ${ring(seconds, periodOf(account), tone)}
        </button>
      `;
    }));

    list.innerHTML = rows.join('');
  }

  async function loadAccounts() {
    try {
      await window.IndooneCloudAccounts?.load?.();
    } catch (error) {
      console.warn('Sankalp: accounts could not be loaded', error);
    }

    accounts = Array.isArray(window.indooneState?.accounts)
      ? window.indooneState.accounts.slice()
      : [];
  }

  function startTicking() {
    stopTicking();

    // One timer for the screen, not one per row: the code changes when the period rolls over,
    // and the ring every second.
    timer = window.setInterval(() => {
      void paint();
    }, 1000);
  }

  function stopTicking() {
    if (timer) {
      window.clearInterval(timer);
      timer = null;
    }
  }

  function openAccount(id) {
    const account = accounts.find(item => String(item.id) === String(id));
    if (!account) return;

    window.indooneState = window.indooneState || {};
    window.indooneState.activeAccount = account;
    window.openMenuNested?.('accounts/edit');
  }

  async function toggleFavourite(id) {
    const account = accounts.find(item => String(item.id) === String(id));
    if (!account) return;

    const next = { ...account, favorite: !account.favorite };
    account.favorite = next.favorite;

    try {
      await window.IndooneCloudAccounts?.save?.(next);
    } catch (error) {
      account.favorite = !next.favorite;
      console.warn('Sankalp: favourite could not be saved', error);
    }

    void paint();
  }

  function bind() {
    el('accountsList')?.addEventListener('click', event => {
      const star = event.target.closest('[data-favourite-id]');

      if (star) {
        event.preventDefault();
        event.stopPropagation();
        void toggleFavourite(star.dataset.favouriteId);
        return;
      }

      const card = event.target.closest('[data-account-id]');

      if (card) openAccount(card.dataset.accountId);
    });

    el('accountsList')?.addEventListener('keydown', event => {
      const star = event.target.closest('[data-favourite-id]');

      if (star && (event.key === 'Enter' || event.key === ' ')) {
        event.preventDefault();
        void toggleFavourite(star.dataset.favouriteId);
      }
    });

    el('accountsSearch')?.addEventListener('input', event => {
      query = String(event.target.value || '').trim().toLowerCase();
      void paint();
    });

    el('accountsSort')?.addEventListener('click', () => {
      sortMode = sortMode === 'favourite' ? 'name' : 'favourite';
      void paint();
    });

    el('accountsEmptyAdd')?.addEventListener('click', () => {
      window.openMenuFeature?.('accounts-add');
    });

    document.querySelector('.accounts-page [data-close]')?.addEventListener('click', () => {
      window.closeMenuPage?.();
    });
  }

  window.initAccountsList = async function initAccountsList() {
    await loadAccounts();
    bind();
    await paint();
    startTicking();

    // The timer belongs to the screen. Leaving it behind kept it painting into a page that was
    // no longer on display.
    window.addEventListener('hashchange', stopTicking, { once: true });

    return true;
  };

  window.IndooneAccountsList = { stop: stopTicking, refresh: paint };
})();
