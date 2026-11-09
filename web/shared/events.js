(() => {
  const $ = id => document.getElementById(id);
  const overlay = $('overlay');
  const ADD_ROUTE_PREFIX = '#lobby/accounts/add-account';

  let authResolved = false;
  let authGateRunning = false;
  let authStateResolved = false;

  function markAuthReady() {
    if (authResolved) return;

    authResolved = true;
    document.body.classList.add('auth-ready');
    document.body.classList.remove('auth-boot');
  }

  function renderLoginNow() {
    // A signed-in session must never be covered by the sign-in screen. This
    // used to call showLogin() whenever the overlay was not already showing an
    // .auth-page, which put the beige backdrop back over the app after a
    // restored session - the half-page of colour in the bug report.
    if (window.IndooneFirebase?.auth?.currentUser) {
      markAuthReady();
      return false;
    }

    try {
      if (window.IndooneAuthUI?.showLogin) {
        const overlayNode = $('overlay');
        const modal = $('modal');
        const authOpen = document.body.classList.contains('auth-open');
        const activeInput = document.activeElement;
        const authInputFocused =
          !!activeInput &&
          !!activeInput.closest?.('#modal') &&
          !!activeInput.matches?.(
            'input, textarea, [contenteditable="true"]'
          );

        if (
          authOpen &&
          overlayNode &&
          !overlayNode.classList.contains('hidden') &&
          modal?.querySelector('.auth-page')
        ) {
          markAuthReady();
          return true;
        }

        if (authInputFocused) {
          markAuthReady();
          return true;
        }

        window.IndooneAuthUI.showLogin();
        markAuthReady();
        return true;
      }
    } catch (error) {
      console.error('Sankalp login UI error:', error);
    }

    return false;
  }

  function bindClick(element, handler) {
    if (
      !element ||
      element.dataset.indooneBound === 'true'
    ) {
      return;
    }

    element.dataset.indooneBound = 'true';
    element.addEventListener('click', handler);
  }

  // One implementation, in shared/core.js, so the tab, the brand button and
  // the menu all land in the same place.
  // The app's search field is hidden until the top-bar search icon is tapped
  // (AccountsScreen.kt: searchVisible starts false), and the icon itself is
  // drawn only where the search field lives. The web draws it on the accounts
  // screen and nowhere else.
  function setSearchControlVisible(visible) {
    $('searchBtn')?.toggleAttribute('hidden', !visible);

    if (!visible) {
      $('searchWrap')?.setAttribute('hidden', '');
    }
  }

  function showHome(options) {
    closeDrawer();
    closeModal();
    setSearchControlVisible(false);
    window.showHome?.(options);
  }

  function showAccounts({ remember = true } = {}) {
    if (remember) {
      window.IndoonePageState?.set('home');
    }

    closeDrawer();
    closeModal();

    $('homeContent')?.setAttribute('hidden', '');
    $('content')?.removeAttribute('hidden');
    $('settingsContent')?.setAttribute('hidden', '');
    $('lobbyContent')?.setAttribute('hidden', '');
    $('addBtn')?.removeAttribute('hidden');
    setSearchControlVisible(true);
    $('homeNav')?.classList.add('active');
    $('lobbyNav')?.classList.remove('active');
    $('settingsNav')?.classList.remove('active');

    window.IndooneHome?.restoreHome?.();
  }

  // The menu's Accounts row means the accounts page. It used to click the Home
  // tab, which shows the chat page, so the row landed on the wrong screen.
  window.showAccounts = showAccounts;

  function showSettingsPanel({ remember = true } = {}) {
    if (remember) {
      window.IndoonePageState?.set('settings');
    }

    closeDrawer();
    $('homeContent')?.setAttribute('hidden', '');
    $('content')?.removeAttribute('hidden');
    $('lobbyContent')?.setAttribute('hidden', '');
    $('addBtn')?.setAttribute('hidden', '');
    setSearchControlVisible(false);
    $('settingsNav')?.classList.add('active');
    $('homeNav')?.classList.remove('active');
    $('lobbyNav')?.classList.remove('active');

    window.showSettings?.({ remember: false });
  }

  // Lobby is the accounts screen. AccountsScreen.kt is what the app shows when
  // Lobby is the active tab - "Your accounts", the Sort button, a row per
  // account with its code and countdown, and the "+" button - and the web has
  // had that screen all along behind #content. The tab used to open a
  // placeholder saying the Lobby was still being built, which was wrong.
  //
  // The accounts page itself is showAccounts above; this is the same view with
  // Lobby carrying the active tab instead of Home.
  function showLobbyPanel({ remember = true } = {}) {
    if (remember) {
      window.IndoonePageState?.set('lobby');
    }

    closeDrawer();
    closeModal();

    $('homeContent')?.setAttribute('hidden', '');
    $('content')?.removeAttribute('hidden');
    $('settingsContent')?.setAttribute('hidden', '');
    $('lobbyContent')?.setAttribute('hidden', '');
    $('addBtn')?.removeAttribute('hidden');
    setSearchControlVisible(true);

    $('lobbyNav')?.classList.add('active');
    $('homeNav')?.classList.remove('active');
    $('settingsNav')?.classList.remove('active');

    window.IndooneHome?.restoreHome?.();
  }

  function clearStaleAddAccountRoute() {
    if (!window.location.hash.startsWith(ADD_ROUTE_PREFIX)) {
      return;
    }

    history.replaceState(
      {},
      '',
      window.location.pathname + window.location.search
    );
  }

  async function restoreSavedPage() {
    const hash = window.location.hash || '';
    const savedPage =
      window.IndoonePageState?.get?.() || 'home';

    if (hash.startsWith(ADD_ROUTE_PREFIX)) {
      if (savedPage === 'add-account') {
        try {
          await window.IndooneHome?.showAddAccount?.({
            push: false
          });
          return;
        } catch (error) {
          console.warn('Add Account route restore failed:', error);
        }
      } else {
        clearStaleAddAccountRoute();
      }
    }

    switch (savedPage) {
      case 'settings':
        showSettingsPanel({ remember: false });
        break;

      case 'profile':
        showSettingsPanel({ remember: false });
        window.openMenuFeature?.('account');
        break;

      case 'mobile':
        showSettingsPanel({ remember: false });
        window.openMenuFeature?.('account');
        window.openMenuFeature?.('change-mobile-number');
        break;

      case 'email':
        showSettingsPanel({ remember: false });
        window.openMenuFeature?.('account');
        window.openMenuFeature?.('change-email');
        break;




      case 'add-account':
        await window.IndooneHome?.showAddAccount?.({
          push: false
        });
        break;

      case 'home':
      default:
        showHome({ remember: false });
        break;
    }
  }

  if (!localStorage.getItem('indoone_otp_verified_uid')) {
    renderLoginNow();
  }

  bindClick($('menuBtn'), () => window.toggleMenu?.());
  bindClick($('addBtn'), () => window.showAdd?.());
  bindClick($('emptyAddBtn'), () => window.showAdd?.());
  bindClick($('clearSearch'), () => window.IndooneHomeSearch?.clear?.());
  bindClick($('brandBtn'), showHome);
  bindClick($('homeNav'), showHome);
  bindClick($('settingsNav'), showSettingsPanel);
  bindClick($('lobbyNav'), showLobbyPanel);
  // Vibe keeps the button it has on Home: same call, same screen.
  bindClick($('vibeNav'), () => window.IndooneVibe?.open?.());
  bindClick(
    $('sortBtn'),
    () => window.handleSortAccounts?.()
  );

  $('drawerBackdrop')?.addEventListener(
    'click',
    () => window.closeDrawer?.()
  );

  $('drawer')?.addEventListener('click', event => {
    const item = event.target.closest('[data-action]');
    if (!item) return;

    const action = item.dataset.action;

    if (action === 'favorites') {
      return window.showFavorites?.();
    }

    if (action === 'trash') {
      return window.showTrash?.();
    }

    if (action === 'security') {
      return window.showSecurity?.();
    }

    if (action === 'about') {
      return window.openMenuFeature?.('about');
    }

    if (action === 'danger-zone') {
      return window.showDangerZone?.();
    }

    if (action === 'logout') {
      return window.showLogout?.();
    }
  });

  document.addEventListener('click', event => {
    const card = event.target.closest('.account');

    if (
      card &&
      !event.target.closest('[data-fav]')
    ) {
      window.openAccount?.(card.dataset.id);
    }

    const fav = event.target.closest('[data-fav]');

    if (fav) {
      event.stopPropagation();
      window.toggleFavorite?.(fav.dataset.fav);
    }
  });

  document.addEventListener('keydown', event => {
    const card = event.target.closest('.account');

    if (
      card &&
      (event.key === 'Enter' || event.key === ' ')
    ) {
      event.preventDefault();
      window.openAccount?.(card.dataset.id);
    }
  });

  overlay?.addEventListener('click', event => {
    if (
      event.target === overlay ||
      event.target.closest('[data-close]')
    ) {
      return window.closeModal?.();
    }

    if (event.target.closest('[data-camera]')) {
      return window.IndooneQrScanner?.start?.();
    }

    if (event.target.closest('[data-save-account]')) {
      return window.saveAccount?.();
    }

    if (event.target.closest('[data-copy]')) {
      return window.copyCurrentCode?.();
    }

    if (event.target.closest('[data-delete]')) {
      return window.deleteCurrent?.();
    }

    if (event.target.closest('[data-edit]')) {
      return window.editCurrent?.();
    }

    if (event.target.closest('[data-about]')) {
      return window.openMenuFeature?.('about');
    }

    if (event.target.closest('[data-security]')) {
      return window.showSecurity?.();
    }

    if (event.target.closest('[data-sort-modal]')) {
      window.handleSortAccounts?.();
      return window.closeModal?.();
    }

    if (event.target.closest('[data-stop-scan]')) {
      window.IndooneQrScanner?.stop?.();
      return window.closeModal?.();
    }
  });

  async function loadFirebaseAccounts() {
    if (!window.IndooneCloudAccounts?.load) {
      throw new Error(
        'Firebase account storage is unavailable.'
      );
    }

    return window.IndooneCloudAccounts.load();
  }

  window.loadFirebaseAccounts = loadFirebaseAccounts;

  async function showAuthOrHome(user) {
    if (authGateRunning) return;

    authGateRunning = true;

    try {
      if (window.__indooneAuthPending) return;

      const persistedUid = localStorage.getItem(
        'indoone_otp_verified_uid'
      );

      if (!user) {
        if (persistedUid && !authStateResolved) return;

        localStorage.removeItem('indoone_otp_verified_uid');
        localStorage.removeItem('indoone_authenticated_uid');
        sessionStorage.removeItem('indoone_otp_verified_uid');
        sessionStorage.removeItem('indoone_authenticated_uid');
        renderLoginNow();
        return;
      }

      authStateResolved = true;

      // Nothing else closes the sign-in screen on this path. firebase-auth-otp
      // closes it, but a session that is simply restored never went through the
      // OTP flow - so the backdrop stayed up over the app, which is the panel of
      // colour in the bug report. Close it here, before anything else runs.
      window.IndooneAuthUI?.close?.();

      if (!persistedUid || persistedUid !== user.uid) {
        localStorage.removeItem('indoone_otp_verified_uid');
        localStorage.removeItem('indoone_authenticated_uid');
        sessionStorage.removeItem('indoone_otp_verified_uid');
        sessionStorage.removeItem('indoone_authenticated_uid');

        await window.IndooneFirebase?.auth?.signOut?.().catch(
          () => {}
        );

        renderLoginNow();
        return;
      }

      sessionStorage.setItem(
        'indoone_otp_verified_uid',
        persistedUid
      );
      sessionStorage.setItem(
        'indoone_authenticated_uid',
        persistedUid
      );

      await loadFirebaseAccounts();
      await restoreSavedPage();

      if (typeof startDemoTimers === 'function') {
        startDemoTimers();
      }
    } catch (error) {
      console.error('Sankalp auth gate error:', error);
      renderLoginNow();
    } finally {
      authGateRunning = false;
      markAuthReady();
    }
  }

  window.addEventListener('load', async () => {
    const auth = window.IndooneFirebase?.auth;

    window.__indooneAuthPending = false;

    if (!auth) {
      return renderLoginNow();
    }

    try {
      await (
        window.IndooneFirebase?.persistenceReady ||
        Promise.resolve()
      );
    } catch (error) {
      console.warn(
        'Sankalp Firebase persistence initialization failed:',
        error
      );
    }

    if (typeof auth.onAuthStateChanged === 'function') {
      auth.onAuthStateChanged(showAuthOrHome);

      // Belt and braces. Whatever order the auth scripts settle in, a signed-in
      // session must never be left under the sign-in backdrop.
      const clearAuthScreenIfSignedIn = () => {
        if (!auth.currentUser) return;

        const overlayNode = document.getElementById('overlay');

        if (overlayNode?.classList.contains('hidden')) return;

        window.IndooneAuthUI?.close?.();
        overlayNode?.classList.remove('indoone-auth-screen');
        overlayNode?.classList.add('hidden');
        document.body.classList.remove('auth-open', 'auth-pending');
      };

      [400, 1500, 4000].forEach(delay =>
        setTimeout(clearAuthScreenIfSignedIn, delay)
      );

      setTimeout(() => {
        if (
          !authStateResolved &&
          !auth.currentUser &&
          !localStorage.getItem('indoone_otp_verified_uid')
        ) {
          renderLoginNow();
        }
      }, 5000);
    } else {
      renderLoginNow();
    }
  });

  if (document.readyState === 'loading') {
    document.addEventListener(
      'DOMContentLoaded',
      () => window.IndooneHome?.init?.(),
      { once: true }
    );
  } else {
    window.IndooneHome?.init?.();
  }
})();
