// The four bottom-navigation icons, drawn to match the app.
//
// The app's bar is AppBottomNav in android/menu/AppPageChrome.kt and it uses
// Icons.Filled.Home, Icons.Outlined.Groups, Icons.Outlined.Link and
// Icons.Outlined.Settings. The web was drawing a pair of people for Home, a
// diamond for Lobby and sliders for Settings, so none of
// the four matched. These are the app's four.
(() => {
  const ICONS = {
    home: [
      '<path d="M12 3.2 3.6 10.3V20a1 1 0 0 0 1 1h4.9v-5.7h5V21h4.9a1 1 0 0 0 1-1v-9.7Z"',
      ' fill="currentColor" stroke="none"></path>'
    ].join(''),

    lobby: [
      '<circle cx="9" cy="8" r="3"></circle>',
      '<circle cx="16.5" cy="8.6" r="2.4"></circle>',
      '<path d="M3.6 19c.5-3.1 2.4-4.6 5.4-4.6s4.9 1.5 5.4 4.6"></path>',
      '<path d="M16.2 14.7c1.9.3 3.1 1.7 3.4 4.3"></path>'
    ].join(''),


    settings: [
      '<circle cx="12" cy="12" r="3.1"></circle>',
      '<path d="M19.1 14.6a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H2.7a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3h.1a1.6 1.6 0 0 0 1-1.5V2.7a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8v.1a1.6 1.6 0 0 0 1.5 1h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z"></path>'
    ].join('')
  };

  function makeIcon(type, className) {
    const svg = document.createElementNS(
      'http://www.w3.org/2000/svg',
      'svg'
    );

    svg.setAttribute('class', className);
    svg.setAttribute('data-shared-nav-icon', type);
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '1.8');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.innerHTML = ICONS[type];

    return svg;
  }

  const NAV_MAP = {
    homeNav: 'home',
    lobbyNav: 'lobby',
    settingsNav: 'settings'
  };

  function apply() {
    Object.entries(NAV_MAP).forEach(([id, type]) => {
      const button = document.getElementById(id);

      if (!button) {
        return;
      }

      const current = button.querySelector('[data-shared-nav-icon]');

      if (current) {
        return;
      }

      button
        .querySelectorAll('.nav-icon, .lobby-nav-icon')
        .forEach(node => node.remove());

      button.insertBefore(
        makeIcon(type, 'nav-icon'),
        button.firstChild
      );
    });

    document
      .querySelectorAll('.lobby-status-icon')
      .forEach(element => {
        if (element.querySelector('[data-shared-nav-icon="lobby"]')) {
          return;
        }

        element.textContent = '';
        element.appendChild(
          makeIcon('lobby', 'ui-svg-icon lobby-svg-icon')
        );
      });
  }

  window.IndooneNavigationIcons = {
    apply,
    icons: ICONS
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', apply, { once: true });
  } else {
    apply();
  }

  if (typeof MutationObserver !== 'undefined') {
    let queued = false;

    const observer = new MutationObserver(() => {
      if (queued) {
        return;
      }

      queued = true;
      queueMicrotask(() => {
        queued = false;
        apply();
      });
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true
    });
  }
})();
