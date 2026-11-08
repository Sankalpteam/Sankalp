(() => {
  const drawer = document.getElementById('drawer');
  const panel = drawer?.querySelector('.drawer-panel');
  const backdrop = drawer?.querySelector('.drawer-backdrop');
  const loadedScripts = new Set();
  const loadedStyles = new Set();
  // Bumped with the three plugin stylesheets added in this change: they are
  // fetched as <base>/style.css?v=<this>, and that exact URL used to 404, so a
  // browser that cached the miss would otherwise keep asking for it.
  // One version for every asset this loader pulls in, and the same string is written into
  // index.html's tags. They have to move together: index.html pinned menu/script.js to a
  // version from October while this constant had been bumped several times, so a returning
  // browser kept the old loader, which kept asking for the old section scripts - and the
  // plugins page went on showing a provider that had been removed weeks earlier.
  const ASSET_VERSION = '20261108d';
  let suppressNextUnderlyingClick = false;

  const featureInitializers = {
    favorites: 'initMenuFavorites',
    trash: 'initMenuTrash',
    security: 'initMenuSecurity',
    'terms-of-use': 'initMenuTermsOfUse',
    'privacy-policy': 'initMenuPrivacyPolicy',
    about: 'initMenuAbout',
    // The Settings row's About is a different screen from the drawer's About,
    // so it gets its own key and its own folder (see the note under
    // featurePaths).
    'settings-about': 'initSettingsAbout',
    'danger-zone': 'initMenuDangerZone',
    logout: 'initMenuLogout',
    profile: 'initMenuProfile',
    account: 'initMenuAccount',
    memory: 'initMenuMemory',
    'chat-history': 'initMenuChatHistory',
    'data-privacy': 'initMenuDataPrivacy',
    plugins: 'initMenuPlugins',
    update: 'initMenuUpdate',
    'change-mobile-number': 'initMenuChangeMobile',
    'change-email': 'initMenuChangeEmail'
  };

  // Two sections moved out of the menu and into settings, and one was renamed
  // to match the app, so the action name and the folder are no longer the same.
  const featurePaths = {
    'danger-zone': 'settings/danger-zone',
    logout: 'settings/logout',
    // About is NOT listed, for the same reason as profile: the drawer's About
    // is android/menu/about/MenuAboutScreen.kt, and getFeatureBase resolves
    // 'about' to menu/about, which draws it in full - About Indoone, Sync, OTP
    // Standard, Account Storage, Legal, Licenses. The Settings row's About is
    // android/settings/about/AboutScreen.kt, a different screen, and it is
    // listed below under 'settings-about'.
    //
    // Profile is NOT listed: the drawer's Profile is android/menu/profile/
    // ProfileMenuScreen.kt - the name, nickname and profession editor - and
    // that is what getFeatureBase resolves 'profile' to. The Settings row's
    // Profile is a different screen, android/settings/profile/ProfileScreen.kt
    // - the account screen - and it has its own key, 'account'. Listing
    // 'profile' here made the drawer open the settings screen instead of its
    // own, and both scripts defined the same initMenuProfile, so one silently
    // overwrote the other.
    account: 'settings/profile',
    // The Settings row's About Indoone is android/settings/about/AboutScreen.kt
    // - the sheet with Version and Updates - which is a different screen from
    // the drawer's About (android/menu/about/MenuAboutScreen.kt). getFeatureBase
    // resolves 'about' to menu/about, so the settings screen needs its own key.
    'settings-about': 'settings/about',
    update: 'settings/update',
    'change-mobile-number': 'settings/profile/change-mobile-number',
    'change-email': 'settings/profile/change-email'
  };

  const nestedInitializers = {
    'danger-zone/delete-account': 'initMenuDeleteAccount',
    'logout/this-device': 'initMenuLogoutThisDevice',
    // the plugin screens sit under Plugins, as they do in the app: GitHub's
    // Browse, and each provider's own screen
    // The paths have to be the folders' own names, because getFeatureBase turns
    // them into menu/<path>. The Zoho Mail folder is zoho_mail, not zoho-mail,
    // and the email screen sits under email/gmail, not email - so those two
    // used to resolve to folders with no index.html and the screens never
    // opened.
    'plugins/github': 'initMenuPluginGitHub',
    'plugins/zoho_mail': 'initMenuPluginZohoMail',
    'plugins/email/gmail': 'initMenuPluginEmail'
  };

  const createSvg = paths => {
    const svg = document.createElementNS(
      'http://www.w3.org/2000/svg',
      'svg'
    );
    svg.setAttribute('width', '20');
    svg.setAttribute('height', '20');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '1.8');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.style.flex = '0 0 20px';
    svg.innerHTML = paths.join('');
    return svg;
  };

  const termsIcon = () => createSvg([
    '<path d="M7 3.5h7l3.5 3.5v13.5H7z"></path>',
    '<path d="M14 3.5V7h3.5"></path>',
    '<path d="M9.5 11h5"></path>',
    '<path d="M9.5 14.5h5"></path>',
    '<path d="M9.5 18h3.5"></path>'
  ]);

  const privacyIcon = () => createSvg([
    '<path d="M2.5 12s3.3-5 9.5-5 9.5 5 9.5 5-3.3 5-9.5 5-9.5-5-9.5-5z"></path>',
    '<circle cx="12" cy="12" r="2.2"></circle>'
  ]);

  function createLegalMenuItem(type, label) {
    const item = document.createElement('button');
    const icon = type === 'terms' ? termsIcon() : privacyIcon();
    item.type = 'button';
    item.className = 'drawer-item';
    item.dataset.action = type === 'terms' ? 'terms-of-use' : 'privacy-policy';
    item.appendChild(icon);
    const text = document.createElement('span');
    text.textContent = label;
    item.appendChild(text);
    return item;
  }

  function ensureLegalMenuItems() {
    if (!panel) return;
    const securityItem = panel.querySelector('[data-action="security"]');
    const aboutItem = panel.querySelector('[data-action="about"]');
    if (!securityItem || !aboutItem) return;
    if (!panel.querySelector('[data-action="terms-of-use"]')) {
      securityItem.insertAdjacentElement('afterend', createLegalMenuItem('terms', 'Terms of Use'));
    }
    if (!panel.querySelector('[data-action="privacy-policy"]')) {
      const termsItem = panel.querySelector('[data-action="terms-of-use"]');
      termsItem?.insertAdjacentElement('afterend', createLegalMenuItem('privacy', 'Privacy Policy'));
    }
  }

  // A menu feature is a page. MenuDrawer.kt replaces the whole screen, and the
  // web was putting each one into a 430px card over a dimmed page. The card
  // and the dimming are gone; the feature takes the shell's page area, and the
  // four tabs keep their place on the right.
  const PAGE_IDS = [
    'homeContent',
    'content',
    'lobbyContent',
    'settingsContent'
  ];

  // which page to come back to, remembered when the first feature is opened
  let pageBeforeMenu = null;

  // and which menu page, when one menu page opened another. GitHub's Browse
  // opens GitHub from Plugins, and the app's back returns to Plugins.
  let menuStack = [];

  function visiblePage() {
    return PAGE_IDS.find(id => {
      const element = document.getElementById(id);
      return element && !element.hasAttribute('hidden');
    }) || 'homeContent';
  }

  function showMenuPage() {
    const page = document.getElementById('modal');
    if (!page) return;

    if (pageBeforeMenu === null) pageBeforeMenu = visiblePage();
    else menuStack = [];

    PAGE_IDS.forEach(id => {
      document.getElementById(id)?.setAttribute('hidden', '');
    });
    document.getElementById('addBtn')?.setAttribute('hidden', '');
    document.getElementById('searchWrap')?.setAttribute('hidden', '');

    page.removeAttribute('hidden');
    page.scrollTop = 0;
  }

  // Going back puts the page that was there before, exactly as it was. Nothing
  // about that page is rebuilt, so nothing about it can be disturbed.
  function closeMenuPage() {
    const page = document.getElementById('modal');
    if (!page || page.hasAttribute('hidden')) return;

    // one menu page opening another: back goes to the one underneath
    const under = menuStack.pop();

    if (under) {
      void openPath(under.path, under.initializer);
      return;
    }

    const back = pageBeforeMenu || 'homeContent';
    pageBeforeMenu = null;

    page.setAttribute('hidden', '');
    document.getElementById(back)?.removeAttribute('hidden');

    const tab = back === 'content' ? 'homeNav'
      : back === 'lobbyContent' ? 'lobbyNav'
      : back === 'settingsContent' ? 'settingsNav'
      : 'homeNav';

    ['homeNav', 'lobbyNav', 'settingsNav'].forEach(id => {
      document.getElementById(id)?.classList.toggle('active', id === tab);
    });

    if (back === 'content') {
      document.getElementById('addBtn')?.removeAttribute('hidden');
      document.getElementById('searchWrap')?.removeAttribute('hidden');
    } else {
      document.getElementById('addBtn')?.setAttribute('hidden', '');
      document.getElementById('searchWrap')?.setAttribute('hidden', '');
    }
  }

  window.closeMenuPage = closeMenuPage;

  // Leaving every menu page, for the flows that jump straight to Home: the
  // drawer's New Chat, opening a saved conversation, and finishing Add account.
  // closeMenuPage() only steps back ONE level, so on a nested page (Plugins ->
  // GitHub) it re-opens the parent underneath instead of clearing the way home.
  // That is why the menu page stayed on screen behind Home.
  function closeAllMenuPages() {
    const page = document.getElementById('modal');

    menuStack = [];
    pageBeforeMenu = null;
    page?.setAttribute('hidden', '');
  }

  window.closeAllMenuPages = closeAllMenuPages;

  // The features' own close buttons carry data-close. shared/events.js watches
  // for those on the overlay, which is where a feature used to live; now that
  // it is a page, the page has to watch for them itself.
  document.getElementById('modal')?.addEventListener('click', event => {
    if (event.target.closest('[data-close]')) {
      closeMenuPage();
    }
  });

  function getFeatureBase(path) {
    return featurePaths[path] || `menu/${path}`;
  }

  async function loadStyle(base) {
    if (loadedStyles.has(base)) return;

    // The four information pages share one stylesheet. It is loaded here, with the version,
    // rather than by an @import inside each style.css: an @import carries no query string, so
    // the browser kept serving its cached copy and a colour change never arrived.
    const shared = document.createElement('link');
    shared.rel = 'stylesheet';
    shared.href = `menu/legal-shared.css?v=${ASSET_VERSION}`;
    document.head.appendChild(shared);

    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = `${base}/style.css?v=${ASSET_VERSION}`;
    document.head.appendChild(link);
    loadedStyles.add(base);
  }

  async function loadScript(base) {
    const src = `${base}/script.js?v=${ASSET_VERSION}`;
    if (loadedScripts.has(src)) return;
    await new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = src;
      script.onload = resolve;
      script.onerror = () => reject(new Error('Menu feature script could not be loaded.'));
      document.body.appendChild(script);
    });
    loadedScripts.add(src);
  }

  async function loadFeatureMarkup(base) {
    const response = await fetch(`${base}/index.html?v=${ASSET_VERSION}`, { cache: 'no-store' });
    if (!response.ok) throw new Error('Menu feature could not be loaded.');
    const modal = document.getElementById('modal');
    if (!modal) throw new Error('Menu modal is unavailable.');
    modal.innerHTML = await response.text();
  }

  async function openPath(path, initializerName) {
    closeDrawer();

    // a menu page opening another keeps the one underneath, so back returns to it
    const open = document.getElementById('modal');
    if (open && !open.hasAttribute('hidden') && open.dataset.menuPath) {
      menuStack.push({ path: open.dataset.menuPath, initializer: open.dataset.menuInitializer });
    }

    try {
      const base = getFeatureBase(path);
      await loadFeatureMarkup(base);
      await loadStyle(base);
      await loadScript(base);
      const initializer = window[initializerName];
      if (typeof initializer !== 'function') throw new Error('Menu feature initializer is unavailable.');
      const result = await initializer();
      if (result === false) closeMenuPage();
      else {
        const shown = document.getElementById('modal');
        if (shown) {
          shown.dataset.menuPath = path;
          shown.dataset.menuInitializer = initializerName;
        }
        showMenuPage();
      }
    } catch (error) {
      closeMenuPage();
      window.toast?.(error?.message || 'Could not open menu item');
    }
  }

  // On a wide screen the panel also comes out when the pointer reaches the
  // left edge. It slides over the app; the app never moves aside.
  const hoverZone = document.getElementById('drawerHoverZone');

  hoverZone?.addEventListener('mouseenter', () => {
    drawer?.classList.add('open');
    drawer?.setAttribute('aria-hidden', 'false');
  });

  document
    .getElementById('drawerClose')
    ?.addEventListener('click', () => window.closeDrawer?.());

  window.toggleMenu = function () {
    if (!drawer) return;
    ensureLegalMenuItems();
    const open = !drawer.classList.contains('open');
    drawer.classList.toggle('open', open);
    drawer.setAttribute('aria-hidden', String(!open));
  };

  window.closeDrawer = function () {
    drawer?.classList.remove('open');
    drawer?.setAttribute('aria-hidden', 'true');
  };

  // The four tabs and the brand leave a menu page behind when they are used.
  ['homeNav', 'lobbyNav', 'settingsNav'].forEach(id => {
    document.getElementById(id)?.addEventListener('click', closeMenuPage);
  });
  document.getElementById('brandBtn')?.addEventListener('click', closeMenuPage);

  window.openMenuFeature = function (feature) {
    const initializerName = featureInitializers[feature];
    if (!initializerName) return;
    void openPath(feature, initializerName);
  };

  window.openMenuNested = function (path) {
    const initializerName = nestedInitializers[path];
    if (!initializerName) return;
    void openPath(path, initializerName);
  };

  panel?.addEventListener('click', event => {
    const item = event.target.closest('[data-action]');
    if (!item || !panel.contains(item)) return;
    event.preventDefault();
    event.stopPropagation();
    const action = item.dataset.action;
    if (action === 'new-chat') {
      closeDrawer();
      window.IndooneChatStore?.clear?.();
      window.showHome?.();
      window.IndooneChatHome?.refresh?.();
      return;
    }

    if (action === 'lock') {
      // MenuDrawer.kt opens UnlockAppActivity when app lock is on and shows
      // "App Lock is not enabled." otherwise. The web has no device app lock,
      // so the message is what it shows; the row used to do nothing at all.
      closeDrawer();
      window.toast?.('App Lock is not enabled.');
      return;
    }

    window.openMenuFeature(action);
  });

  const closeFromOutside = event => {
    if (!drawer?.classList.contains('open')) return;
    if (panel && panel.contains(event.target)) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation?.();
    suppressNextUnderlyingClick = true;
    closeDrawer();
  };

  drawer?.addEventListener('pointerdown', closeFromOutside);
  backdrop?.addEventListener('pointerdown', closeFromOutside, true);

  document.addEventListener('click', event => {
    if (!suppressNextUnderlyingClick) return;
    suppressNextUnderlyingClick = false;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation?.();
  }, true);
})();
