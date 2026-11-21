// The one turn that is in flight, so a refresh does not lose it.
//
// `/api/chat` answers in the same response, so a tab that is refreshed or closed while
// the request is open never sees the reply. The user's message is already saved to the
// account before the call, so the thread comes back after a reload - with the user's
// turn and no answer, and nothing saying why.
//
// This keeps a small record of the turn while the request is open. On the next load the
// composer is filled with it again and the thread says it was not delivered.
//
// Deliberately NOT an automatic retry. A lost request may have been a GitHub write - a
// branch, a commit - and re-sending it on load would repeat that side effect without
// anyone asking. The user decides.
(() => {
  const KEY = 'indoone_chat_inflight_v1';

  function cacheKey() {
    const uid = window.IndooneFirebase?.auth?.currentUser?.uid;
    return uid ? `${KEY}:${uid}` : null;
  }

  function read() {
    const key = cacheKey();
    if (!key) return null;
    try {
      // Global legacy records cannot be attributed to an account.
      localStorage.removeItem(KEY);
      const raw = localStorage.getItem(key);

      if (!raw) {
        return null;
      }

      const parsed = JSON.parse(raw);

      if (!parsed || typeof parsed.text !== 'string' || !parsed.text.trim()) {
        return null;
      }

      return parsed;
    } catch (error) {
      // A corrupt record must not break the page.
      return null;
    }
  }

  function begin({ conversationId, text, hadAttachments = false, agentMode = false, clientTurnId = null }) {
    const value = String(text || '').trim();

    if (!value) {
      return;
    }

    const key = cacheKey();
    if (!key) return;
    try {
      localStorage.setItem(
        key,
        JSON.stringify({
          conversationId: conversationId || null,
          text: value,
          // A File cannot be brought back after the page is gone - the browser gives no
          // handle to it once the document dies. The record remembers that there was
          // one, so the restore can say so instead of silently sending text only.
          hadAttachments: Boolean(hadAttachments),
          agentMode: Boolean(agentMode),
          clientTurnId: clientTurnId || null,
          wasDispatched: false,
          startedAt: Date.now()
        })
      );
    } catch (error) {
      console.warn('Sankalp in-flight turn could not be recorded:', error);
    }
  }

  // Set before the network request. A browser refresh after dispatch must not
  // restore a ready-to-resend draft: the remote write may already have happened.
  function markDispatched() {
    const key = cacheKey();
    const record = read();
    if (!key || !record) return;
    try {
      localStorage.setItem(key, JSON.stringify({ ...record, wasDispatched: true }));
    } catch (error) {
      console.warn('Sankalp could not mark an in-flight request:', error);
    }
  }

  function finish() {
    const key = cacheKey();
    if (!key) return;
    try {
      localStorage.removeItem(key);
    } catch (error) {
      // Nothing to do: the record is a convenience, not the source of truth.
    }
  }

  function pending() {
    return read();
  }

  window.IndooneInflight = {
    begin,
    markDispatched,
    finish,
    pending,
    clear: finish
  };
})();
