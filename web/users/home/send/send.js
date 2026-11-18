// Sending: the pending attachments, the user bubble, the call, the reply, and
// the save to the account. HomeScreen's sendMessage() does the same, including
// the three activity labels.
(() => {
  const MAX_ATTACHMENTS = 10;
  let pending = [];
  let sending = false;
  let activeAbortController = null;
  let activeTurnId = 0;

  // The welcome panel belongs to an empty thread, and it is the sender's job to take it away.
  //
  // It used to be removed only by paintThread() in chat-home.js, which runs on a refresh or when
  // the answer lands - so on the web the greeting stayed on screen over the message you had just
  // sent, and disappeared when the reply arrived. The app removes it the moment you send. One
  // product, two behaviours, and the web's was the wrong one.
  function paint(activity) {
    const messages = window.IndooneChatStore?.getMessages?.() || [];
    const welcome = document.getElementById('chatWelcome');

    window.IndooneMessages?.render(document.getElementById('chatList'), messages, activity ?? null);
    welcome?.toggleAttribute('hidden', messages.length > 0);
    window.IndooneChatScroll?.toBottom?.();
  }

  function clearActivity() {
    paint(null);
  }

  function setActivity(state) {
    paint(state);
  }

  function abortActiveRequest() {
    if (activeAbortController) {
      activeAbortController.abort();
      activeAbortController = null;
    }
  }

  function isImage(item) {
    return String(item.file?.type || '').startsWith('image/');
  }

  function extensionOf(name) {
    const parts = String(name || '').split('.');
    const ext = parts.length > 1 ? parts.pop() : '';
    return (ext || 'FILE').slice(0, 4).toUpperCase();
  }

  function renderPending() {
    const host = document.getElementById('pendingAttachments');

    if (!host) {
      return;
    }

    host.textContent = '';

    if (!pending.length) {
      host.setAttribute('hidden', '');
      window.IndooneChatHome?.syncComposer?.();
      return;
    }

    host.removeAttribute('hidden');

    pending.forEach((item, index) => {
      const wrap = document.createElement('div');
      wrap.className = 'pending-item';

      const thumb = document.createElement('div');
      thumb.className = 'pending-thumb';

      if (isImage(item) && item.url) {
        const image = document.createElement('img');
        image.src = item.url;
        image.alt = item.file.name || 'Attached photo preview';
        thumb.appendChild(image);
      } else {
        const badge = document.createElement('span');
        badge.className = 'pending-badge';
        badge.textContent = extensionOf(item.file?.name);
        thumb.appendChild(badge);
      }

      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'pending-remove';
      remove.setAttribute('aria-label', 'Remove attachment');
      remove.textContent = '×';
      remove.disabled = sending;
      remove.addEventListener('click', () => {
        if (sending) {
          return;
        }

        if (item.url) {
          URL.revokeObjectURL(item.url);
        }

        pending.splice(index, 1);
        renderPending();
      });

      thumb.appendChild(remove);

      const name = document.createElement('span');
      name.className = 'pending-name';
      name.textContent = item.file?.name || 'attachment';

      wrap.appendChild(thumb);
      wrap.appendChild(name);
      host.appendChild(wrap);
    });

    window.IndooneChatHome?.syncComposer?.();
  }

  function attach(file) {
    if (!file) {
      return;
    }

    if (pending.length >= MAX_ATTACHMENTS) {
      window.IndooneMessages?.render(
        document.getElementById('chatList'),
        [
          ...(window.IndooneChatStore?.getMessages?.() || []),
          { text: 'You can attach up to 10 files per message.', mine: false }
        ],
        null
      );
      window.IndooneChatScroll?.toBottom?.();
      return;
    }

    pending.push({
      file,
      url: isImage({ file }) ? URL.createObjectURL(file) : null
    });
    renderPending();
  }

  // keepUrls holds the object URLs that a sent message still points at. Only
  // the ones nobody kept are revoked.
  function clearPending(keepUrls) {
    pending.forEach(item => {
      if (item.url && !(keepUrls && keepUrls.has(item.url))) {
        URL.revokeObjectURL(item.url);
      }
    });
    pending = [];
    renderPending();
  }

  function setBusy(busy) {
    sending = busy;

    const send = document.getElementById('sendBtn');
    const arrow = send?.querySelector('.send-arrow');
    const spinner = send?.querySelector('.send-spinner');

    if (arrow) {
      arrow.hidden = busy;
    }

    if (spinner) {
      spinner.hidden = !busy;
    }

    window.IndooneChatHome?.syncComposer?.();
  }

  // The text node of the last reply on screen, so a streaming answer can be written into it
  // without rebuilding the list.
  //
  // It looks at the last reply ROW, not the last text node: a reply that starts empty renders
  // no text node at all, so searching for one finds the previous message's node and the new
  // answer would be written over it.
  function lastReplyTextNode() {
    const list = document.getElementById('chatList');

    if (!list) {
      return null;
    }

    const rows = list.querySelectorAll('.chat-row.theirs');

    if (!rows.length) {
      return null;
    }

    const row = rows[rows.length - 1];
    const existing = row.querySelector('.chat-text');

    if (existing) {
      return existing;
    }

    const bubble = row.querySelector('.chat-bubble');

    if (!bubble) {
      return null;
    }

    const node = document.createElement('span');
    node.className = 'chat-text';
    bubble.appendChild(node);
    return node;
  }

  async function send() {
    const input = document.getElementById('chatInput');
    const text = (input?.value || '').trim();
    const attachments = pending.slice();
    const store = window.IndooneChatStore;

    if (!store || sending || (!text && !attachments.length)) {
      return;
    }

    const message = text || (
      'Please inspect the attached file(s) and describe their contents ' +
      'or visible details.'
    );

    const sentAttachments = attachments.map(item => ({
      name: item.file.name,
      type: item.file.type,
      url: item.url
    }));

    const mine = store.add({
      text,
      mine: true,
      attachments: sentAttachments
    });

    if (input) {
      input.value = '';
      input.style.height = 'auto';
    }

    const conversationId = store.ensureConversationId();
    const turnId = ++activeTurnId;
    let streamedElement = null;
    const controller = new AbortController();
    activeAbortController = controller;

    clearPending(new Set(sentAttachments.map(item => item.url).filter(Boolean)));
    setBusy(true);
    setActivity('received');
    await new Promise(resolve => setTimeout(resolve, 350));
    if (activeTurnId !== turnId) {
      return;
    }
    setActivity('thinking');

    // Thinking stays on screen for at least a moment, the way it does on the phone.
    //
    // The two clients already run the same sequence - Received, Thinking, gone on the first
    // word - but a desktop line returns the first token in a few hundred milliseconds, so
    // Thinking was a flash and the screen looked like it had one label. The app is on a mobile
    // connection and Thinking sits there for seconds. This is the web's wait brought up to the
    // same floor, not a different sequence.
    const thinkingShownAt = Date.now();
    const THINKING_MIN_MS = 900;

    void window.IndooneCloudChat
      ?.appendMessage?.(conversationId, 'user', text || message)
      .catch(error => console.warn('Sankalp chat save failed:', error));

    window.IndooneInflight?.begin({
      conversationId,
      text: message,
      hadAttachments: sentAttachments.length > 0
    });

    try {
      const api = window.IndooneChatApi;

      if (!api) {
        throw new Error('Sankalp is not reachable right now.');
      }

      // The stream is preferred: the first delta is the answer starting, so the indicator
      // goes away then instead of after the whole reply.
      //
      // A turn with an attachment keeps the blocking endpoint, because the stream endpoint
      // takes no attachments - a file sent to it would be silently dropped.
      let result = null;
      let streamedMessage = null;
      let latestText = '';
      let thinkingHold = false;

      if (!sentAttachments.length && typeof api.streamMessage === 'function') {
        try {
          result = await api.streamMessage({
            message,
            conversationId,
            signal: controller.signal,
            onMeta: id => store.setConversationId(id),
            onDelta: (chunk, full) => {
              if (activeTurnId !== turnId) {
                return;
              }

              // While the floor is running, only the newest text is kept - one timer, not one
              // per token.
              if (thinkingHold) {
                latestText = full;
                return;
              }

              if (!streamedMessage) {
                // First token: the wait is over - but not before Thinking has been readable.
                const waited = Date.now() - thinkingShownAt;

                if (waited < THINKING_MIN_MS) {
                  thinkingHold = true;
                  setTimeout(() => {
                    if (activeTurnId !== turnId || streamedMessage) {
                      return;
                    }

                    thinkingHold = false;
                    setActivity(null);
                    streamedMessage = store.add({ text: latestText, mine: false });
                    paint(null);
                    streamedElement = lastReplyTextNode();
                    window.IndooneChatScroll?.toBottom?.();
                  }, THINKING_MIN_MS - waited);
                  latestText = full;
                  return;
                }

                setActivity(null);
                streamedMessage = store.add({ text: '', mine: false });
                paint(null);
                streamedElement = lastReplyTextNode();
              }

              latestText = full;
              streamedMessage.text = full;

              // Only the bubble that is still being written is updated.
              //
              // This used to re-render the whole thread on every token: with a long answer
              // and a long history that is thousands of DOM rebuilds, and it threw away the
              // reader's scroll position, text selection and any button state with each one.
              if (streamedElement) {
                streamedElement.textContent = full;
              } else {
                paint(null);
                streamedElement = lastReplyTextNode();
              }

              window.IndooneChatScroll?.toBottom?.();
            }
          });
        } catch (error) {
          if (error?.name === 'AbortError') {
            throw error;
          }

          // Do not automatically POST the same turn again. The backend could
          // have executed a GitHub/email write before the stream was lost.
          // Retrying without an idempotency key risks executing it twice.
          console.warn('Sankalp stream interrupted; outcome unconfirmed:', error);

          if (streamedMessage) {
            store.remove(streamedMessage);
            streamedMessage = null;
          }

          const uncertain = new Error(
            'The connection was interrupted. This request may already have been processed. ' +
            'Check the conversation and connected app before trying again.'
          );
          uncertain.name = 'UncertainRequestError';
          throw uncertain;
        }
      }

      if (!result) {
        result = await api.send({
          message,
          conversationId,
          attachments,
          signal: controller.signal
        });
      }

      if (activeTurnId !== turnId) {
        return;
      }

      store.setConversationId(result.conversationId);
      setActivity(null);
      window.IndooneInflight?.finish();

      if (streamedMessage) {
        // The streamed bubble becomes the finished one: same text, plus the chips the
        // reply offered.
        streamedMessage.text = result.reply;
        streamedMessage.chips = result.chips || [];
        paint(null);
      } else {
        store.add({ text: result.reply, mine: false, chips: result.chips || [] });
      }

      void window.IndooneCloudChat
        ?.appendMessage?.(result.conversationId, 'assistant', result.reply)
        .catch(error => console.warn('Sankalp chat save failed:', error));
    } catch (error) {
      if (error?.name === 'AbortError') {
        return;
      }

      console.error('Sankalp chat send failed:', error);
      window.IndooneInflight?.finish();
      // Keep the user's question visible when the backend outcome is unknown.
      if (error?.name !== 'UncertainRequestError') {
        store.remove(mine);
      }
      store.add({
        text:
          error?.message &&
          !/undefined|not a function|null/i.test(error.message)
            ? error.message
            : 'Sankalp could not complete the request.',
        mine: false
      });
    } finally {
      if (activeAbortController === controller) {
        activeAbortController = null;
      }
      if (activeTurnId === turnId) {
        setBusy(false);
        clearActivity();
        const messages = store.getMessages();
        document.getElementById('chatWelcome')?.toggleAttribute('hidden', messages.length > 0);
        window.IndooneChatScroll?.toBottom?.();
        window.IndooneChatHome?.syncComposer?.();
      }
    }
  }

  window.IndooneSend = {
    send,
    attach,
    clearPending,
    getPending: () => pending,
    cancel: abortActiveRequest
  };
})();
