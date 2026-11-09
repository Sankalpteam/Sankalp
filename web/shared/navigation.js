window.showFavorites = async function () {
  closeModal?.();

  try {
    if (window.IndooneCloudAccounts?.load) {
      await window.IndooneCloudAccounts.load();
    }

    const favorites = (
      window.indooneState?.accounts || []
    ).filter(a => a.favorite);

    const rows = favorites.length
      ? favorites
          .map(
            a => `
              <button
                type="button"
                class="settings-row"
                style="width:100%;text-align:left;border:0;background:#fff;"
                onclick="closeModal();openAccount(${Number(a.id)})"
              >
                <span>
                  ${a.name}
                  <small>${a.email || 'Authenticator account'}</small>
                </span>
                <b>${a.code || '------'}</b>
              </button>
            `
          )
          .join('')
      : `<p style="text-align:center;padding:28px 0">No favorite accounts yet.</p>`;

    openModal(`
      <div class="modal-head">
        <h2>Favorites</h2>
        <button class="close-btn" data-close>×</button>
      </div>
      <div id="favoritesList">${rows}</div>
      <button class="primary" data-close>Done</button>
    `);
  } catch (error) {
    toast(error?.message || 'Could not load favorites');
  }
};

window.showTrash = function () {
  openModal(`
    <div class="modal-head">
      <h2>Trash</h2>
      <button class="close-btn" data-close>×</button>
    </div>
    <p style="text-align:center;padding:28px 0">
      Deleted accounts are permanently removed from Firebase. Nothing is kept in Trash.
    </p>
    <button class="primary" data-close>Done</button>
  `);
};

window.showSecurity = function () {
  openModal(`
    <h2>Security</h2>
    <div class="settings-row">
      <span>
        Firebase account storage
        <small>
          Authenticator accounts synced to your user account
        </small>
      </span>
      <b>✓</b>
    </div>
    <div class="settings-row">
      <span>
        Local account storage
        <small>
          Not used for permanent account data
        </small>
      </span>
      <b>✓</b>
    </div>
    <button class="primary" data-close>Done</button>
  `);
};

// Keep Lock App packed with the menu items instead of pinning it to the bottom.
document.querySelector('.lock-item')?.style.setProperty('margin-top', '0');

// Keep a clear, consistent space between the Sankalp brand and the menu button.
function applyTopbarBrandSpacing() {
  const topbarLeft = document.querySelector('.topbar-left');

  if (topbarLeft) {
    topbarLeft.style.gap = '14px';
  }
}

// The drawer's icons used to be built here, from a second map of eight paths,
// inserted as the first child of each row. The menu now carries the app's own
// icons through data-app-icon and shared/app-icons.js - all fourteen of them,
// the same shapes MenuDrawer.kt names - so this map and its inserter are gone.
// Leaving them in put a second icon beside every row they knew about, which is
// what the menu showed: two grids on Accounts, two stars on Favorites, two
// bins on Trash, two shields on Security, two marks on About and two on Lock.

// Main UI glyphs: keep their existing meaning and position, but render them as SVGs.
const MAIN_SVG_ICONS = {
  search:
    '<circle cx="11" cy="11" r="6.5"></circle><path d="m16 16 4 4"></path>',
  close:
    '<path d="m7 7 10 10M17 7 7 17"></path>',
  sort:
    '<path d="M7 5v14M4 8l3-3 3 3M17 19V5M14 16l3 3 3-3"></path>',
  empty:
    '<path d="M4 12h16M12 4v16"></path>',
  phone:
    '<rect x="6.5" y="3.5" width="11" height="17" rx="2"></rect><path d="M10 17.5h4"></path>',
  laptop:
    '<rect x="5" y="5" width="14" height="10" rx="1.5"></rect><path d="M3 18h18M9 18l-1 2h8l-1-2"></path>',
  back:
    '<path d="m14 5-7 7 7 7"></path>',
  next:
    '<path d="m10 5 7 7-7 7"></path>'
};

function makeMainSvg(
  type,
  className = 'ui-svg-icon'
) {
  const paths = MAIN_SVG_ICONS[type];
  if (!paths) return null;

  const svg = document.createElementNS(
    'http://www.w3.org/2000/svg',
    'svg'
  );

  svg.setAttribute('class', className);
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.innerHTML = paths;

  return svg;
}

function replaceTextNodeIcon(element, iconType) {
  if (
    !element ||
    element.querySelector('.ui-svg-icon')
  ) {
    return;
  }

  const icon = makeMainSvg(iconType);
  if (!icon) return;

  element.insertBefore(icon, element.firstChild);

  const textNodes = Array.from(element.childNodes).filter(
    node =>
      node.nodeType === Node.TEXT_NODE &&
      node.textContent.trim()
  );

  if (textNodes.length) {
    textNodes[0].textContent = textNodes[0].textContent
      .replace(
        /^[\s\u2302\u2315\u2300\u2195\u2194\u2303\u203a\u2039\u2302\u2022]+/u,
        ''
      )
      .trimStart();
  }
}

function applyMainSvgIcons() {
  const search = document.getElementById('searchBtn');

  if (
    search &&
    !search.querySelector('.ui-svg-icon')
  ) {
    const icon = makeMainSvg(
      'search',
      'ui-svg-icon topbar-svg-icon'
    );
    search.textContent = '';
    search.appendChild(icon);
  }

  const searchBoxIcon = document.querySelector(
    '.search-box > span:first-child'
  );

  if (
    searchBoxIcon &&
    !searchBoxIcon.querySelector('.ui-svg-icon')
  ) {
    searchBoxIcon.textContent = '';
    searchBoxIcon.appendChild(
      makeMainSvg('search')
    );
  }

  const sort = document.getElementById('sortBtn');

  if (
    sort &&
    !sort.querySelector('.ui-svg-icon')
  ) {
    sort.textContent = 'Sort ';
    sort.appendChild(makeMainSvg('sort'));
  }

  const empty = document.querySelector('.empty-icon');

  if (
    empty &&
    !empty.querySelector('.ui-svg-icon')
  ) {
    empty.textContent = '';
    empty.appendChild(makeMainSvg('empty'));
  }







}

function applySvgIconStyles() {
  if (document.getElementById('svg-icon-runtime-style')) {
    return;
  }

  const style = document.createElement('style');
  style.id = 'svg-icon-runtime-style';
  style.textContent = `
    .ui-svg-icon {
      width: 22px;
      height: 22px;
      display: block;
      flex: none;
      color: currentColor;
    }

    .topbar-svg-icon {
      width: 21px;
      height: 21px;
      margin: auto;
    }

    .search-box .ui-svg-icon {
      width: 18px;
      height: 18px;
    }

    #sortBtn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
    }

    #sortBtn .ui-svg-icon {
      width: 15px;
      height: 15px;
    }

    .empty-icon .ui-svg-icon {
      width: 27px;
      height: 27px;
    }









  `;
  document.head.appendChild(style);
}

function applyAllSvgIcons() {
  applyTopbarBrandSpacing();
  applySvgIconStyles();
  applyMainSvgIcons();
}

if (document.readyState === 'loading') {
  document.addEventListener(
    'DOMContentLoaded',
    applyAllSvgIcons,
    { once: true }
  );
} else {
  applyAllSvgIcons();
}

// Pages such as Logout are loaded into the modal after startup.
// Re-apply only when the DOM actually changes so dynamically inserted controls use the same SVG set.
if (typeof MutationObserver !== 'undefined') {
  let iconObserverQueued = false;

  const iconObserver = new MutationObserver(() => {
    if (iconObserverQueued) return;

    iconObserverQueued = true;
    queueMicrotask(() => {
      iconObserverQueued = false;
      applyAllSvgIcons();
    });
  });

  iconObserver.observe(document.body, {
    childList: true,
    subtree: true
  });
}
