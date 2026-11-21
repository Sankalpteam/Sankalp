// The conversation. It is saved to the account through cloud-chat.js, which is
// what the app does; localStorage is only a cache so a reload is instant.
(() => {
  const KEY = 'indoone_chat_thread_v1';
  const MAX_MESSAGES = 200;

  let conversationId = null;
  let messages = [];

  function cache() {
    try {
      localStorage.setItem(
        KEY,
        JSON.stringify({ conversationId, messages })
      );
    } catch (error) {
      console.warn('Sankalp chat thread could not be cached:', error);
    }
  }

  function readCache() {
    try {
      const raw = localStorage.getItem(KEY);

      if (!raw) {
        return null;
      }

      return JSON.parse(raw);
    } catch (error) {
      return null;
    }
  }

  function signedIn() {
    return Boolean(window.IndooneFirebase?.auth?.currentUser);
  }

  // Load the account's newest conversation, falling back to the cache when
  // there is nothing in the cloud yet or the account is not reachable.
  async function load() {
    if (signedIn() && window.IndooneCloudChat?.loadLatest) {
      try {
        const conversation = await window.IndooneCloudChat.loadLatest();

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

    const saved = readCache();

    if (saved) {
      conversationId = saved.conversationId || null;
      messages = Array.isArray(saved.messages) ? saved.messages : [];
    }
  }

  function add(message) {
    messages.push(message);

    if (messages.length > MAX_MESSAGES) {
      messages = messages.slice(-MAX_MESSAGES);
    }

    cache();
    return message;
  }

  function remove(message) {
    const index = messages.indexOf(message);

    if (index >= 0) {
      messages.splice(index, 1);
      cache();
    }
  }

  function clear() {
    conversationId = null;
    messages = [];

    // Delete the cache rather than write an empty record over it. An empty record still
    // answers readCache() with a truthy object, which is how a deleted conversation kept
    // coming back: the store cleared itself, and the next load() restored it from storage.
    try {
      localStorage.removeItem(KEY);
    } catch (error) {
      console.warn('Sankalp chat thread could not be cleared:', error);
    }
  }

  function ensureConversationId() {
    if (!conversationId) {
      conversationId = (window.crypto?.randomUUID?.() ||
        `c-${Date.now()}-${Math.random().toString(16).slice(2)}`);
    }

    return conversationId;
  }

  function replace(next) {
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
    getMessages: () => messages,
    getConversationId: () => conversationId,
    setConversationId: id => {
      conversationId = id;
      cache();
    }
  };
})();
