window.initMenuFavorites = async function () {
  const modal = document.getElementById('modal');
  const list = document.getElementById('favoritesList');
  const count = document.getElementById('favoritesCount');

  const escapeHtml = value => String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');

  if (!modal || !list) {
    return;
  }

  try {
    const cloud = window.IndooneCloudAccounts;

    if (!cloud?.load) {
      throw new Error('Account storage is unavailable.');
    }

    await cloud.load();

    const favorites = (window.indooneState?.accounts || [])
      .filter(account => account.favorite)
      .sort((a, b) =>
        String(a.name || '').localeCompare(String(b.name || ''))
      );

    if (count) {
      count.textContent =
        `${favorites.length} favorite account${favorites.length === 1 ? '' : 's'}`;
    }

    if (!favorites.length) {
      list.innerHTML = `
        <div class="favorite-empty">
          <div
            class="favorite-empty-icon"
            aria-hidden="true"
          >
            ★
          </div>
          <h3>
            No favorite accounts
          </h3>
          <p>
            Star an account on the Home page and it will appear here.
          </p>
        </div>
      `;
      return;
    }

    list.innerHTML = favorites
      .map(account => `
        <button
          type="button"
          class="favorite-account-row"
          data-favorite-account="${Number(account.id)}"
        >
          <span
            class="favorite-account-icon ${escapeHtml(account.cls || '')}"
          >
            ${escapeHtml(account.icon || '•')}
          </span>

          <span
            class="favorite-account-main"
          >
            <span class="favorite-account-name">
              ${escapeHtml(account.name || 'Authenticator')}
            </span>
            <span class="favorite-account-email">
              ${escapeHtml(account.email || 'Authenticator account')}
            </span>
          </span>

          <span
            class="favorite-account-side"
          >
            <span class="favorite-account-code">
              ${escapeHtml(account.code || '------')}
            </span>
            <span
              class="favorite-account-arrow"
              aria-hidden="true"
            >
              ›
            </span>
          </span>
        </button>
      `)
      .join('');

    list
      .querySelectorAll('[data-favorite-account]')
      .forEach(row => {
        row.addEventListener('click', () => {
          const id = Number(row.dataset.favoriteAccount);

          closeModal();

          if (id && typeof openAccount === 'function') {
            openAccount(id);
          }
        });
      });
  } catch (error) {
    toast(error?.message || 'Could not load favorites');
  }
};
