(() => {

  // The web counterpart of android/settings/SettingsScreen.kt.
  //
  // The app draws, in this order, with no section heading and no eyebrow above
  // the title:
  //
  //   SettingsMenuItem(ProfileIcon,       "Profile",       ...)
  //   SettingsMenuItem(LockIcon,          "App Lock")
  //   SettingsMenuToggle(BiometricIcon,   "Biometric Unlock", ...)
  //   SettingsMenuItem(TimerIcon,         "Auto-Lock")
  //   SettingsMenuItem(NotificationsIcon, "Notifications")
  //   SettingsMenuAction(InfoIcon,        "Version", versionName,
  //                                       "Check for updates")
  //   SettingsMenuItem(DangerZoneIcon,    "Danger Zone")
  //   SettingsMenuItem(LogoutIcon,        "Log out")
  //
  // Four of those rows are deliberately absent on the web. App Lock, Biometric
  // Unlock and Auto-Lock are device features - a device PIN, a device biometric
  // and a device auto-lock timer - with nothing to do in a browser tab, and
  // Notifications has no web push parity here. They are left out rather than
  // drawn as rows that open nothing.
  //
  // The Version row IS drawn. It is the row the app has where it once had
  // "About Sankalp": the terminal branch replaced SettingsMenuItem(InfoIcon,
  // "About Sankalp") with SettingsMenuAction(InfoIcon, "Version", versionName,
  // "Check for updates"), and the merge brought that into main - so main has no
  // About row here and no android/settings/about/ any more. About Sankalp is
  // still a drawer row, under SECURITY & INFORMATION, and this file does not
  // draw one.
  //
  // The app reads the installed package version for that subtitle
  // (installedVersionName). A browser tab has no installed package, so this
  // reports what web/update.json publishes - the same file settings/update
  // reads. The row's action opens the update screen, which is the app's
  // onCheckForUpdates.
  //
  // Profile, Danger Zone and Log out are menu features whose folders live under
  // settings/ (see featurePaths in menu/script.js). They are opened through
  // openMenuFeature, which loads the fragment, its stylesheet and its script -
  // the same way the drawer opens them.
  const SETTINGS = [
    {
      id: 'profile',
      title: 'Profile',
      icon: 'profile',
      // Opened through the menu loader, exactly like Danger Zone and Log out,
      // so Profile is a full page rather than a dimmed card over the app.
      action: () => window.openMenuFeature?.('account')
    },
    {
      id: 'version',
      title: 'Version',
      icon: 'info',
      subtitle: () => webVersion,
      trailing: 'Check for updates',
      action: () => window.openMenuFeature?.('update')
    },
    {
      id: 'danger-zone',
      title: 'Danger Zone',
      icon: 'warning',
      action: () => window.openMenuFeature?.('danger-zone')
    },
    {
      id: 'logout',
      title: 'Log out',
      icon: 'logout',
      action: () => window.openMenuFeature?.('logout')
    }
  ];

  // The app's Version row reports the installed package version. A browser tab
  // has none, so this reads web/update.json's versionName - the same value
  // settings/update shows. Until it arrives the row draws its title alone,
  // which is what the app does when the version cannot be read.
  let webVersion = '';
  let versionRequested = false;

  function loadVersion() {
    if (versionRequested) {
      return;
    }

    versionRequested = true;

    fetch('update.json', { cache: 'no-store' })
      .then(response => (response.ok ? response.json() : null))
      .then(metadata => {
        webVersion = metadata?.versionName || '';

        // Redraw only when the settings page is the one on screen.
        const page = document.getElementById('settingsContent');

        if (page && !page.hasAttribute('hidden')) {
          renderSettingsPage();
        }
      })
      .catch(() => {});
  }

  const ICONS = {
    // Icons.Outlined.Person, the app's ProfileIcon
    profile: `
      <circle cx="12" cy="8" r="3.1"></circle>
      <path d="M5.5 19.5c.8-3.1 3.1-4.8 6.5-4.8s5.7 1.7 6.5 4.8"></path>
    `,
    // Icons.Outlined.Info, the app's InfoIcon (the Version row)
    info: `
      <circle cx="12" cy="12" r="8"></circle>
      <path d="M12 10.5v5"></path>
      <path d="M12 7.5h.01"></path>
    `,
    // Icons.Outlined.WarningAmber, the app's DangerZoneIcon
    warning: `
      <path d="M12 3.6 2.6 20.2h18.8z"></path>
      <path d="M12 9.6v4.6"></path>
      <path d="M12 17.1h.01"></path>
    `,
    // Icons.Outlined.Logout, the app's LogoutIcon
    logout: `
      <path d="M14.5 4.8H7a2 2 0 0 0-2 2v10.4a2 2 0 0 0 2 2h7.5"></path>
      <path d="m16.6 8.3 3.4 3.7-3.4 3.7"></path>
      <path d="M20 12H10"></path>
    `,
    next: `
      <path d="m9 5 7 7-7 7"></path>
    `
  };

  function makeIcon(type) {
    // createElementNS needs BOTH arguments. Without the tag name this throws
    // "2 arguments required, but only 1 present" on the first row, so
    // renderSettingsPage() died and the Settings tab kept showing the accounts
    // page underneath. Every other createElementNS in web/ passes 'svg'.
    const svg = document.createElementNS(
      'http://www.w3.org/2000/svg',
      'svg'
    );

    svg.setAttribute('class', 'settings-option-icon');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '1.8');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.innerHTML = ICONS[type] || ICONS.info;

    return svg;
  }

  function rowMarkup(item) {
    const subtitle =
      typeof item.subtitle === 'function'
        ? item.subtitle()
        : item.subtitle;

    const icon = makeIcon(item.icon);

    const button = document.createElement('button');
    button.type = 'button';
    button.className =
      'settings-row settings-row-button settings-option-row';
    button.dataset.settingsAction = item.id;

    const iconWrap = document.createElement('span');
    iconWrap.className = 'settings-option-icon-wrap';
    iconWrap.appendChild(icon);

    const text = document.createElement('span');
    text.className = 'settings-row-text';
    // The app's SettingsMenuItem takes subtitle: String? = null and every
    // call leaves it out, so a row with no subtitle shows only its title.
    text.innerHTML = subtitle
      ? `
      <b>${item.title}</b>
      <small>${subtitle}</small>
    `
      : `
      <b>${item.title}</b>
    `;

    // SettingsMenuAction draws its action as text on the row - Text(action,
    // color = Color(0xFF6330DB), fontSize = 12.sp, fontWeight = SemiBold) -
    // where SettingsMenuItem draws an AppChevron.
    const trailing = document.createElement('span');

    if (item.trailing) {
      trailing.className = 'settings-row-action';
      trailing.textContent = item.trailing;
    } else {
      trailing.className = 'settings-arrow';
      trailing.setAttribute('aria-hidden', 'true');
      trailing.appendChild(makeIcon('next'));
    }

    button.append(iconWrap, text, trailing);
    return button;
  }

  function hideSettingsPage() {
    document
      .getElementById('settingsContent')
      ?.setAttribute('hidden', '');
  }

  function showSettingsPage() {
    const page = document.getElementById('settingsContent');
    const content = document.getElementById('content');
    const search = document.getElementById('searchWrap');
    const add = document.getElementById('addBtn');

    content?.setAttribute('hidden', '');
    search?.setAttribute('hidden', '');
    add?.setAttribute('hidden', '');
    page?.removeAttribute('hidden');

    document
      .getElementById('settingsNav')
      ?.classList.add('active');
    document
      .getElementById('homeNav')
      ?.classList.remove('active');
    document
      .getElementById('lobbyNav')
      ?.classList.remove('active');
  }

  function renderSettingsPage() {
    let page = document.getElementById('settingsContent');

    if (!page) {
      page = document.createElement('section');
      page.className = 'settings-content';
      page.id = 'settingsContent';
      page.hidden = true;

      const app = document.getElementById('app');
      const nav = document.querySelector('.bottom-nav');

      app?.insertBefore(page, nav || null);
    }

    page.innerHTML = '';

    // The app's head is the title and one line under it. There is no eyebrow
    // above it and no section heading under it.
    const head = document.createElement('div');
    head.className = 'settings-page-head';
    head.innerHTML = `
      <h1>Settings</h1>
      <p>Manage your account, security and app preferences.</p>
    `;
    page.appendChild(head);

    // Every row is drawn and every row is wired.
    SETTINGS.forEach(item => {
      const row = rowMarkup(item);
      row.addEventListener('click', () => item.action());
      page.appendChild(row);
    });

    return page;
  }

  window.showSettings = function ({ remember = true } = {}) {
    if (remember) {
      window.IndoonePageState?.set('settings');
    }

    closeModal?.();
    renderSettingsPage();
    showSettingsPage();
    loadVersion();
  };

  document.addEventListener('click', event => {
    const target = event.target;

    if (target.closest('#settingsNav')) {
      return;
    }

    if (
      target.closest(
        '#homeNav, #lobbyNav, .drawer-item'
      )
    ) {
      hideSettingsPage();
    }
  });
})();
