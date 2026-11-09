(() => {
  const BRAND_VERSION = '20261109-logo-real-artwork';

  // The mark is the artwork itself, not a shape redrawn in SVG. assets/mark.png is the
  // supplied image with its pale background removed, so it sits on the white header, on
  // the lilac account tile and on the auth screens without a square behind it.
  // The path is taken from this script's own URL, so it resolves the same whether the
  // mark is drawn on a root page or three folders deep.
  const scriptUrl = (document.currentScript && document.currentScript.src) || '';
  const MARK_SRC =
    scriptUrl.replace(/shared\/branding\.js.*$/, '') + 'assets/mark.png';

  function createLogo(className, alt = 'Sankalp logo') {
    const image = document.createElement('img');

    image.className = `${className} branding-image`;
    image.src = MARK_SRC;
    image.alt = alt;
    image.draggable = false;
    image.decoding = 'async';
    image.dataset.indooneBranding = 'true';

    return image;
  }

  function installFavicon() {
    // The icons are static files linked from index.html: 48/96/192 png, a 180 apple touch
    // icon and favicon.ico. Nothing is drawn at runtime, because a crawler reads the HTML
    // it is served and never runs this script - which is how the site ended up with no
    // icon at all in a search result.
    document
      .querySelectorAll('link[data-indoone-favicon="true"]')
      .forEach(link => link.remove());
  }

  function wire(root = document) {
    root
      .querySelectorAll?.('.brand-mark, .auth-mark, .about-mark, .token-icon, .chat-logo')
      .forEach(element => {
        if (element.dataset.indooneBranding === 'true') return;

        const className = element.classList.contains('auth-mark')
          ? 'auth-mark'
          : element.classList.contains('about-mark')
            ? 'about-mark'
            : element.classList.contains('token-icon')
              ? 'token-icon'
              : element.classList.contains('chat-logo')
                ? 'chat-logo'
                : 'brand-mark';

        element.replaceWith(createLogo(className));
      });
  }

  function installStyles() {
    if (document.getElementById('indoone-branding-styles')) return;

    const style = document.createElement('style');
    style.id = 'indoone-branding-styles';
    style.textContent = `
      .branding-image {
        display: block !important;
        flex: 0 0 auto !important;
        width: 38px !important;
        height: 38px !important;
        object-fit: contain !important;
        object-position: center !important;
        max-width: none !important;
        max-height: none !important;
        margin: 0 !important;
        padding: 0 !important;
        border: 0 !important;
        border-radius: 0 !important;
        background: transparent !important;
        box-shadow: none !important;
        color: transparent !important;
        font-size: 0 !important;
        overflow: visible !important;
      }

      .topbar-left .branding-image {
        width: 34px !important;
        height: 34px !important;
        align-self: center !important;
      }

      .chat-logo.branding-image {
        width: 24px !important;
        height: 24px !important;
        /* The rule above sets margin: 0 !important, which was silently wiping
           the 8px the chat mark keeps before a reply. HomeScreen.kt gives its
           mark padding(end = 8.dp), so the reply must start 8px clear of it -
           without this the text sat flush against the mark and the bubble was
           8px wider than the app's. Same specificity, so it wins. */
        margin-right: 8px !important;
      }

      .drawer-brand .branding-image {
        width: 36px !important;
        height: 36px !important;
      }

      .auth-mark.branding-image {
        width: 72px !important;
        height: 72px !important;
      }

      .about-mark.branding-image {
        width: 48px !important;
        height: 48px !important;
        margin: 8px auto 12px !important;
      }

      /* AccountDetailsScreen.kt draws the account's mark as a 58dp tile on
         0xFFF2ECFF, cornered 18dp, with IndooneTopBarLogo(34.dp) centred in it.
         The logo svg replaces the tile here, so without these it was a bare
         48px mark with no tile behind it - which is not what the app shows. */
      .token-icon.branding-image {
        width: 58px !important;
        height: 58px !important;
        /* 58 - 2 x 12 = 34, the app's logo */
        padding: 12px !important;
        box-sizing: border-box !important;
        border-radius: 18px !important;
        background: #f2ecff !important;
        margin: 0 auto !important;
      }
    `;

    document.head.appendChild(style);
  }

  function observe() {
    if (typeof MutationObserver === 'undefined' || !document.body) return;

    const observer = new MutationObserver(records => {
      records.forEach(record => {
        record.addedNodes.forEach(node => {
          if (node.nodeType === Node.ELEMENT_NODE) wire(node);
        });
      });
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true
    });
  }

  function init() {
    installStyles();
    installFavicon();
    wire(document);
    observe();
  }

  window.IndooneBranding = {
    init,
    logoSource: 'assets/mark.png',
    version: BRAND_VERSION
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
