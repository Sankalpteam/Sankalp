(() => {
  // The web counterpart of android/settings/profile/ProfileScreen.kt - the
  // ACCOUNT screen, not the drawer's profile editor, which is menu/profile.
  //
  // This screen is a menu feature, opened by menu/script.js the same way Danger
  // Zone and Log out are: the loader fetches the fragment into #modal, calls
  // this initializer, then shows the page.
  //
  // The app draws three rows here, not two. The third is
  //   ProfileActionRow("Connections & plugins", gmailConnectionSummary)
  // and its description is not a fixed line - ProfileScreen.kt fills it from the
  // Gmail connection status:
  //
  //   "Checking saved connections…"                        while it looks
  //   "Gmail linked · ${status.emailAddress}"               connected, with an address
  //   "Gmail linked to this Sankalp profile"                connected, no address
  //   "Connect Gmail and manage your plugin connections"    not connected
  //   "Could not load saved connections"                    the call failed
  //
  // The same five strings are used here, from the same endpoint the plugins
  // screen reads: api/integrations/gmail/status.

  const BACKEND_URL = 'https://indooneai.duckdns.org';

  function currentUser() {
    return window.IndooneFirebase?.auth?.currentUser || null;
  }

  // The app's sentence, word for word, from the status answer.
  function connectionSummary(status) {
    if (status?.connected && String(status.emailAddress || '').trim()) {
      return `Gmail linked · ${String(status.emailAddress).trim()}`;
    }

    if (status?.connected) {
      return 'Gmail linked to this Sankalp profile';
    }

    return 'Connect Gmail and manage your plugin connections';
  }

  async function renderConnections() {
    const node = document.getElementById('profileConnections');

    if (!node) {
      return;
    }

    node.textContent = 'Checking saved connections…';

    const user = currentUser();

    if (!user?.uid) {
      node.textContent = 'Could not load saved connections';
      return;
    }

    try {
      const idToken = await user.getIdToken(false);
      const response = await fetch(
        `${BACKEND_URL}/api/integrations/gmail/status?user_id=${encodeURIComponent(user.uid)}`,
        { headers: { Authorization: `Bearer ${idToken}` } }
      );

      if (!response.ok) {
        throw new Error(`Sankalp backend returned ${response.status}`);
      }

      const status = await response.json().catch(() => ({}));
      node.textContent = connectionSummary(status);
    } catch (_) {
      node.textContent = 'Could not load saved connections';
    }
  }

  async function renderProfile() {
    const user = currentUser();
    const email = document.getElementById('profileEmail');
    const phone = document.getElementById('profilePhone');

    if (email) {
      email.textContent = user?.email || 'Email not available';
    }

    if (phone) {
      phone.textContent = 'Mobile number not set';
    }

    if (!user || !phone) {
      return;
    }

    try {
      const db = window.IndooneFirebase?.database;

      if (!db) {
        return;
      }

      const snapshot = await db
        .ref(`users/${user.uid}/profile/mobile`)
        .once('value');

      const mobile =
        window.IndooneFirebaseAuthBase?.normalizeMobile?.(
          snapshot.val() || ''
        ) || String(snapshot.val() || '');

      phone.textContent =
        mobile || 'Mobile number not set';
    } catch (_) {
      phone.textContent =
        'Mobile number not available';
    }
  }

  window.initMenuAccount = function () {
    const page = document.getElementById('modal');

    if (!page) {
      return false;
    }

    void renderProfile();
    void renderConnections();

    page
      .querySelectorAll('[data-profile-action]')
      .forEach(button => {
        const action = button.dataset.profileAction;

        if (action === 'mobile') {
          button.addEventListener(
            'click',
            () => window.openMenuFeature?.('change-mobile-number')
          );
        }

        if (action === 'email') {
          button.addEventListener(
            'click',
            () => window.openMenuFeature?.('change-email')
          );
        }

        // The app's onPluginsClick opens the Plugins screen, which is where
        // Gmail is actually connected and disconnected.
        if (action === 'plugins') {
          button.addEventListener(
            'click',
            () => window.openMenuFeature?.('plugins')
          );
        }
      });

    return true;
  };
})();
