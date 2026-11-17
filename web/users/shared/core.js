const overlay = document.getElementById('overlay');
const modal = document.getElementById('modal');

window.IndoonePageState = window.IndoonePageState || {
  key: 'indoone_current_page',

  set(page) {
    try {
      sessionStorage.setItem(
        this.key,
        String(page || 'home')
      );
    } catch (_) {}
  },

  get() {
    try {
      return sessionStorage.getItem(this.key) || '';
    } catch (_) {
      return '';
    }
  },

  clear() {
    try {
      sessionStorage.removeItem(this.key);
    } catch (_) {}
  }
};

function clearHomeSubRoute() {
  const hash = window.location.hash || '';

  if (!hash.startsWith('#lobby/accounts/add-account')) return;

  history.replaceState(
    {},
    '',
    window.location.pathname + window.location.search
  );
}

window.openModal = function (html) {
  if (!modal || !overlay) return;

  modal.innerHTML = html;
  overlay.classList.remove('hidden');
};

window.closeOverlay = function (event) {
  if (event.target === overlay) {
    overlay.classList.add('hidden');
  }
};

window.closeModal = function () {
  overlay?.classList.add('hidden');
  // a menu feature is a page of the shell now, so leaving one is part of
  // closing up, not only hiding the dimming layer
  window.closeMenuPage?.();
};

// The Home tab shows the chat page, which is #homeContent.
//
// This used to unhide #content and the account "+" button, because Home was
// the accounts page before the chat moved in. So pressing Home showed the
// accounts page, left the chat hidden, and floated the accounts "+" over the
// composer. Every page that returns to Home went through this one.
window.showHome = function ({ remember = true } = {}) {
  if (remember) {
    window.IndoonePageState?.set('home');
  }

  clearHomeSubRoute();
  overlay?.classList.add('hidden');
  window.closeDrawer?.();
  // Any menu page goes too. showHome is reached from the drawer's New Chat and
  // from opening a saved conversation, and both of those happen with a menu
  // page open - which it used to leave on screen underneath Home. closeModal
  // above already does this; showHome did not.
  window.closeAllMenuPages?.();

  document.getElementById('homeContent')?.removeAttribute('hidden');
  document.getElementById('content')?.setAttribute('hidden', '');
  document.getElementById('settingsContent')?.setAttribute('hidden', '');
  document.getElementById('lobbyContent')?.setAttribute('hidden', '');
  document.getElementById('addBtn')?.setAttribute('hidden', '');
  document.getElementById('searchWrap')?.setAttribute('hidden', '');

  document
    .querySelectorAll('.bottom-nav button')
    .forEach(button => button.classList.remove('active'));

  document.getElementById('homeNav')?.classList.add('active');

  window.IndooneChatHome?.init?.();
  window.IndooneChatHome?.refresh?.();
};

window.toast = function (message) {
  let t = document.getElementById('toast');

  if (!t) {
    t = document.createElement('div');
    t.id = 'toast';
    t.style.cssText =
      'position:fixed;left:50%;bottom:88px;transform:translateX(-50%);background:#19151f;color:#fff;padding:11px 16px;border-radius:12px;font-size:13px;z-index:9999;box-shadow:0 8px 30px #0003;';
    document.body.appendChild(t);
  }

  t.textContent = message;
  t.style.display = 'block';
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(
    () => {
      t.style.display = 'none';
    },
    1800
  );
};
