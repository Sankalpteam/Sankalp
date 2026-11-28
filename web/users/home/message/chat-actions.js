// Share a whole conversation, and rename it.
//
// The app has both - HomeScreen.kt's shareCurrentChat and renameCurrentChat, against
// POST /api/chat/threads/{id}/share and PATCH /api/chat/threads/{id}. The web had neither: it
// could share a single exchange through the browser's share sheet, and a thread could only ever
// be named by its first line. Two clients, one backend, two different products.
(() => {
  function backendBase() {
    const value = String(window.SANKALP_BACKEND || '').replace(/\/+$/, '');

    if (!value) {
      console.error('Sankalp: config.js did not load, so the backend address is unknown.');
    }

    return value;
  }

  async function authToken() {
    const user = window.firebase?.auth?.().currentUser;

    if (!user) {
      throw new Error('Please sign in again.');
    }

    return user.getIdToken(false);
  }

  function toast(message) {
    if (typeof window.toast === 'function') {
      window.toast(message);
    }
  }

  function currentThread() {
    return String(window.IndooneChatStore?.getConversationId?.() || '').trim();
  }

  async function call(path, options) {
    const token = await authToken();
    const response = await fetch(`${backendBase()}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        ...(options?.headers || {}),
      },
    });

    if (!response.ok) {
      window.sankalpError?.report(new Error(`${response.status} from ${path}`), 'chat action');
      throw new Error(window.sankalpError?.fromStatus(response.status) || 'Something went wrong.');
    }

    return response.status === 204 ? null : response.json();
  }

  async function copy(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (error) {
      // Clipboard access is denied in some browsers without a user gesture.
      return false;
    }
  }

  async function shareThread() {
    const thread = currentThread();

    if (!thread) {
      toast('Send a message first');
      return;
    }

    const body = await call(`/api/chat/threads/${encodeURIComponent(thread)}/share`, {
      method: 'POST',
    });
    const url = String(body?.share_url || '').trim();

    if (!url) {
      // The backend answered but not with a link. That is our fault, not the user's, and the
      // sentence should say so rather than describing the shape of a response they never saw.
      throw new Error('The share link could not be made just now. Please try again.');
    }

    // The share sheet first, where there is one. A link is meant to be sent, and putting it on
    // the clipboard means the person still has to find somewhere to paste it - and what they
    // paste is text until an app decides otherwise. navigator.share passes it as a URL.
    if (typeof navigator.share === 'function') {
      try {
        await navigator.share({ title: 'Sankalp chat', url });
        return;
      } catch (error) {
        // A cancelled share sheet is not a failure worth reporting; anything else falls
        // through to the clipboard, which always works.
        if (error?.name === 'AbortError') return;
      }
    }

    if (await copy(url)) {
      toast('Link copied — anyone with it can view this chat');
    } else {
      window.prompt('Copy this link:', url);
    }
  }

  async function renameThread(title) {
    const thread = currentThread();
    const value = String(title || '').trim();

    if (!thread || !value) {
      return false;
    }

    await call(`/api/chat/threads/${encodeURIComponent(thread)}`, {
      method: 'PATCH',
      body: JSON.stringify({ title: value }),
    });

    // The account's own copy is what Chat History lists, so it is renamed too. A failure here
    // must not undo the rename that already succeeded on the backend.
    try {
      await window.IndooneCloudChat?.renameConversation?.(thread, value);
    } catch (error) {
      console.warn('Sankalp chat title was not saved to the account:', error);
    }

    toast('Chat renamed');
    return true;
  }

  function closeMenu() {
    const menu = document.getElementById('chatActionsMenu');
    menu?.setAttribute('hidden', '');
  }

  function wire() {
    const button = document.getElementById('chatActionsBtn');
    const menu = document.getElementById('chatActionsMenu');
    const share = document.getElementById('chatShareBtn');
    const rename = document.getElementById('chatRenameBtn');
    const dialog = document.getElementById('chatRenameDialog');
    const input = document.getElementById('chatRenameInput');
    const save = document.getElementById('chatRenameSave');
    const cancel = document.getElementById('chatRenameCancel');

    if (!button || !menu) {
      return;
    }

    button.addEventListener('click', event => {
      event.stopPropagation();
      menu.toggleAttribute('hidden');
    });

    document.addEventListener('click', closeMenu);

    share?.addEventListener('click', () => {
      closeMenu();
      shareThread().catch(error => toast(error?.message || 'Could not create a share link'));
    });

    rename?.addEventListener('click', () => {
      closeMenu();
      const store = window.IndooneChatStore;
      const first = store?.getMessages?.().find(message => message.mine);
      input.value = String(first?.text || '').trim().slice(0, 120);
      dialog?.removeAttribute('hidden');
      input?.focus();
    });

    cancel?.addEventListener('click', () => dialog?.setAttribute('hidden', ''));

    save?.addEventListener('click', () => {
      const value = String(input?.value || '').trim();

      if (!value) {
        toast('A chat needs a name');
        return;
      }

      dialog?.setAttribute('hidden', '');
      renameThread(value).catch(error => toast(error?.message || 'Could not rename this chat'));
    });
  }

  window.IndooneChatActions = { shareThread, renameThread };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', wire);
  } else {
    wire();
  }
})();
