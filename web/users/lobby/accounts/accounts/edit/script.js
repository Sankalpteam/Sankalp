(() => {
  const MARKUP_URL =
    'lobby/accounts/accounts/edit/index.html?v=20261018b';
  const STYLE_URL =
    'lobby/accounts/accounts/edit/style.css?v=20261018b';

  async function loadMarkup() {
    const response = await fetch(MARKUP_URL, {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error(
        'Account details page could not be loaded.'
      );
    }

    return response.text();
  }

  function loadStyle() {
    const baseUrl = STYLE_URL.split('?')[0];

    if (
      document.querySelector(
        `link[href^="${baseUrl}"]`
      )
    ) {
      return;
    }

    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = STYLE_URL;
    document.head.appendChild(link);
  }

  function findAccount(id) {
    return (window.indooneState?.accounts || []).find(
      account => Number(account.id) === Number(id)
    );
  }

  function fillAccount(account) {
    const modal = document.getElementById('accountDialog');

    if (!modal || !account) {
      return false;
    }

    const seconds = Number(
      account.seconds ?? account.period ?? 30
    );
    const period = Number(account.period || 30);
    const digits = Number(account.digits || 6);
    const algorithm = account.algorithm || 'SHA1';
    const progress = Math.max(
      5,
      Math.min(100, (seconds / period) * 100)
    );

    const name = modal.querySelector(
      '#homeAccountName'
    );
    const icon = modal.querySelector(
      '#homeAccountIcon'
    );
    const email = modal.querySelector(
      '#homeAccountEmail'
    );
    const code = modal.querySelector(
      '#homeAccountCode'
    );
    const progressBar = modal.querySelector(
      '#homeAccountProgress'
    );
    const countdown = modal.querySelector(
      '#homeAccountCountdown'
    );
    const periodText = modal.querySelector(
      '#homeAccountPeriod'
    );
    const algorithmText = modal.querySelector(
      '#homeAccountAlgorithm'
    );
    const digitsText = modal.querySelector(
      '#homeAccountDigits'
    );

    if (name) {
      name.textContent = account.name || 'Account';
    }

    if (icon) {
      icon.textContent = account.icon || 'A';
    }

    if (email) {
      email.textContent =
        account.email || 'Authenticator account';
    }

    if (code) {
      code.textContent = account.code || '------';
    }

    if (progressBar) {
      progressBar.style.width = `${progress}%`;
    }

    if (countdown) {
      countdown.textContent = `${seconds}s`;
    }

    if (periodText) {
      periodText.textContent = `${period} seconds`;
    }

    if (algorithmText) {
      algorithmText.textContent = algorithm;
    }

    if (digitsText) {
      digitsText.textContent = `${digits} digits`;
    }

    modal.dataset.accountId = String(account.id);
    return true;
  }

  async function open(id) {
    const account = findAccount(id);
    const modal = document.getElementById('accountDialog');
    const overlay = document.getElementById('overlay');

    if (!account || !modal || !overlay) {
      return;
    }

    try {
      await refreshAccountCodes();
    } catch (_) {
      // Keep the current account visible even when a refresh fails.
    }

    const refreshedAccount = findAccount(id);

    if (!refreshedAccount) {
      return;
    }

    loadStyle();

    try {
      modal.innerHTML = await loadMarkup();
      fillAccount(refreshedAccount);
      overlay.classList.remove('hidden');
    } catch (error) {
      toast(error?.message || 'Could not open account');
    }
  }

  async function toggleFavorite(id) {
    const account = findAccount(id);

    if (!account) {
      return;
    }

    const next = !account.favorite;

    try {
      const saved = {
        ...account,
        favorite: next,
        updatedAt: Date.now()
      };

      await window.IndooneCloudAccounts?.save?.(saved);
      account.favorite = next;
      renderAccounts();
      toast(
        next
          ? 'Added to favorites'
          : 'Removed from favorites'
      );
    } catch (error) {
      toast(
        error?.message || 'Could not update favorite'
      );
    }
  }

  async function deleteCurrent() {
    const modal = document.getElementById('accountDialog');
    const id = Number(
      modal?.dataset.accountId || 0
    );
    const account = findAccount(id);

    if (!account) {
      return;
    }

    try {
      await window.IndooneCloudAccounts?.remove?.(id);

      window.indooneState.accounts =
        window.indooneState.accounts.filter(
          item => Number(item.id) !== id
        );

      renderAccounts();
      closeModal();
      toast('Account moved to Trash for 30 days');
    } catch (error) {
      toast(
        error?.message ||
          'Could not move account to Trash'
      );
    }
  }

  function editCurrent() {
    const modal = document.getElementById('accountDialog');
    const id = Number(
      modal?.dataset.accountId || 0
    );
    const account = findAccount(id);

    if (!account) {
      toast('Account not found');
      return;
    }

    return window.IndooneAccountEdit?.open?.(
      account
    );
  }

  async function copyCurrentCode() {
    const modal = document.getElementById('accountDialog');
    const id = Number(
      modal?.dataset.accountId || 0
    );
    const account = findAccount(id);

    if (!account) {
      return;
    }

    try {
      await navigator.clipboard?.writeText?.(
        String(account.code || '').replace(/\s/g, '')
      );
      toast('Code copied');
    } catch (_) {
      toast('Could not copy code');
    }
  }

  window.openAccount = open;
  window.toggleFavorite = toggleFavorite;
  window.deleteCurrent = deleteCurrent;
  window.editCurrent = editCurrent;
  window.copyCurrentCode = copyCurrentCode;

  window.IndooneHomeAccount = {
    open
  };
})();
