// The conversation. It is saved to the account through cloud-chat.js, which is
// what the app does; localStorage is only a cache so a reload is instant.
(() => {
  const KEY = 'indoone_chat_thread_v1';
  const MAX_MESSAGES = 200;

  function currentUid() {
    return window.IndooneFirebase?.auth?.currentUser?.uid || null;
  }

  let ownerUid = null;
  function ensureOwner() {
    const uid = currentUid();
    if (uid !== ownerUid) {
      conversationId = null;
      messages = [];
      ownerUid = uid;
    }
    return uid;
  }

  // Never reuse the previous account's browser cache on a shared device.
  function cacheKey() {
    const uid = ensureOwner();
    return uid ? `${KEY}:${uid}` : null;
  }

  let conversationId = null;
  let messages = [];

  function cache() {
    const key = cacheKey();
    if (!key) return;
    try {
      localStorage.setItem(
        key,
        JSON.stringify({ conversationId, messages })
      );
    } catch (error) {
      console.warn('Sankalp chat thread could not be cached:', error);
    }
  }

  function readCache() {
    const key = cacheKey();
    if (!key) return null;
    try {
      const raw = localStorage.getItem(key);

      if (!raw) {
        return null;
      }

      return JSON.parse(raw);
    } catch (error) {
      return null;
    }
  }

  function signedIn() {
    return Boolean(ensureOwner());
  }

  // Load the account's newest conversation, falling back to the cache when
  // there is nothing in the cloud yet or the account is not reachable.
  async function load() {
    const uid = ensureOwner();
    conversationId = null;
    messages = [];
    if (!uid) return;

    // A legacy global cache has no identity ownership: never restore it.
    try { localStorage.removeItem(KEY); } catch {}

    if (signedIn() && window.IndooneCloudChat?.loadLatest) {
      try {
        const conversation = await window.IndooneCloudChat.loadLatest();
        if (currentUid() !== uid) return;

        if (conversation) {
          conversationId = conversation.id;
          messages = conversation.messages.map(item => ({
            text: item.content,
            mine: item.role === 'user'
          }));
          cache();
          return;
        }

        // Signed in, and the account has no conversations. A cache entry here is not a
        // conversation to restore - it is one that was deleted, and restoring it reopens the
        // thread the person just removed. The cache is for being offline, not for overriding
        // an account that has answered.
        clear();
        return;
      } catch (error) {
        console.warn('Sankalp chat could not be read from the account:', error);
      }
    }

    if (currentUid() !== uid) return;
    const saved = readCache();

    if (saved) {
      conversationId = saved.conversationId || null;
      messages = Array.isArray(saved.messages) ? saved.messages : [];
    }
  }

  function add(message) {
    if (!ensureOwner()) return message;
    messages.push(message);

    if (messages.length > MAX_MESSAGES) {
      messages = messages.slice(-MAX_MESSAGES);
    }

    cache();
    return message;
  }

  function remove(message) {
    ensureOwner();
    const index = messages.indexOf(message);

    if (index >= 0) {
      messages.splice(index, 1);
      cache();
    }
  }

  function clear() {
    ensureOwner();
    conversationId = null;
    messages = [];

    // Delete the cache rather than write an empty record over it. An empty record still
    // answers readCache() with a truthy object, which is how a deleted conversation kept
    // coming back: the store cleared itself, and the next load() restored it from storage.
    try {
      const key = cacheKey();
      if (key) localStorage.removeItem(key);
    } catch (error) {
      console.warn('Sankalp chat thread could not be cleared:', error);
    }
  }

  function ensureConversationId() {
    if (!ensureOwner()) throw new Error('Please sign in again.');
    if (!conversationId) {
      conversationId = (window.crypto?.randomUUID?.() ||
        `c-${Date.now()}-${Math.random().toString(16).slice(2)}`);
    }

    return conversationId;
  }

  function replace(next) {
    if (!ensureOwner()) return;
    messages = Array.isArray(next) ? next.slice(-MAX_MESSAGES) : [];
    cache();
  }

  window.IndooneChatStore = {
    load,
    replace,
    add,
    remove,
    clear,
    ensureConversationId,
    getMessages: () => { ensureOwner(); return messages; },
    getConversationId: () => { ensureOwner(); return conversationId; },
    setConversationId: id => {
      if (!ensureOwner()) return;
      conversationId = id;
      cache();
    }
  };
})();
