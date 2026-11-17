// The app sorts by name and nothing else: AccountsState.kt defaults to
// DESCENDING (name Z->A) and the Sort button toggles to ASCENDING
// (AccountsViewModel). There is no newest-first state in the app.
window.indooneState = {
  accounts: [],
  search: '',
  sortAscending: false,
  trash: []
};

window.handleSortAccounts = function () {
  indooneState.sortAscending = !indooneState.sortAscending;
  renderAccounts();
};

window.refreshAccountCodes = async function () {
  const now = Math.floor(Date.now() / 1000);

  for (const a of indooneState.accounts) {
    if (!a.secret) continue;

    a.period = Number(a.period || 30);
    a.digits = Number(a.digits || 6);
    a.algorithm = a.algorithm || 'SHA1';
    a.seconds = a.period - (now % a.period);

    try {
      a.code = await TOTP.generate(
        a.secret,
        Math.floor(now / a.period),
        a.digits,
        a.algorithm
      );
    } catch (_) {
      a.code = '------';
    }
  }

  renderAccounts();
};

window.renderAccounts = function () {
  const {
    accounts,
    search,
    sortAscending
  } = indooneState;

  // Search matches only the account/service name, not the email address.
  let list = accounts.filter(a =>
    String(a.name || '')
      .toLowerCase()
      .includes(search)
  );

  list = [...list].sort(
    sortAscending
      ? (a, b) => String(a.name || '').localeCompare(String(b.name || ''))
      : (a, b) => String(b.name || '').localeCompare(String(a.name || ''))
  );

  const listEl = document.getElementById('accountList');

  listEl.innerHTML = list.map(a => {
    const seconds = Number(a.seconds ?? 30);
    const timerClass =
      seconds >= 15
        ? 'timer timer-green'
        : (seconds >= 5
          ? 'timer timer-yellow'
          : 'timer timer-red');
    const codeClass =
      seconds >= 15
        ? 'code-green'
        : (seconds >= 5
          ? 'code-yellow'
          : 'code-red');

    return `
    <article
      class="account"
      data-id="${a.id}"
      tabindex="0"
    >
      <div class="service-icon ${a.cls}">${a.icon}</div>
      <div class="account-info">
        <b>${a.name}</b>
        <span>${a.email}</span>
        <strong class="${codeClass}">${a.code || '------'}</strong>
      </div>
      <button
        class="fav ${a.favorite ? 'active' : ''}"
        data-fav="${a.id}"
        aria-label="Favorite"
      >
        ${a.favorite ? '★' : '☆'}
      </button>
      <div class="${timerClass}">
        ${seconds}
      </div>
    </article>
  `;
  }).join('');

  document.getElementById('emptyState').hidden = list.length > 0;
};

window.startDemoTimers = function () {
  setInterval(refreshAccountCodes, 1000);
  refreshAccountCodes();
};
