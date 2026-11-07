window.initMenuChatHistory = function () {
  const list = document.getElementById('menuChatHistoryList');
  const status = document.getElementById('menuChatHistoryStatus');
  const count = document.getElementById('menuChatHistoryCount');

  function setStatus(text) {
    if (status) {
      status.textContent = text;
    }
  }

  // Today reads as a time, anything older as a date - the app does the same.
  const IST = { timeZone: 'Asia/Kolkata' };

  function whenLabel(millis) {
    if (!millis) {
      return '';
    }

    const then = new Date(Number(millis));

    // Asia/Kolkata, always - the zone the app formats in, so the same conversation reads the
    // same on both. The browser's own zone put a message in the wrong day for anyone whose
    // device is set elsewhere.
    const sameDay =
      then.toLocaleDateString(IST, { day: '2-digit', month: '2-digit', year: 'numeric' }) ===
      new Date().toLocaleDateString(IST, { day: '2-digit', month: '2-digit', year: 'numeric' });

    if (sameDay) {
      return then.toLocaleTimeString(IST, { hour: 'numeric', minute: '2-digit' });
    }

    return then.toLocaleDateString(IST, { day: 'numeric', month: 'short' });
  }

  function metaLine(chat) {
    const parts = [];
    const size = Number(chat.messageCount) || 0;

    if (size > 0) {
      parts.push(size + (size === 1 ? ' message' : ' messages'));
    }

    const when = whenLabel(chat.updatedAt);

    if (when) {
      parts.push(when);
    }

    return parts.join(' · ');
  }

  // Hides, rather than removes: the button that opened the menu has to be able
  // to close it again, and a removed element cannot be un-hidden.
  function closeMenus() {
    document.querySelectorAll('.ch-menu').forEach(menu => {
      menu.hidden = true;
    });
  }

  async function openChat(id) {
    window.closeModal?.();

    try {
      const conversation = await window.IndooneCloudChat?.loadConversation?.(id);

      if (conversation) {
        window.IndooneChatStore?.setConversationId?.(id);
        window.IndooneChatStore?.replace?.(
          conversation.messages.map(item => ({
            text: item.content,
            mine: item.role === 'user'
          }))
        );
      }
    } catch (error) {
      console.warn('Indoone chat could not be opened:', error);
    }

    window.showHome?.();
  }

  async function removeChat(id) {
    try {
      // deleteConversation drops the conversation, its messages and the index -
      // the whole of it, which is what the app's delete does.
      await window.IndooneCloudChat?.deleteConversation?.(id);
      await load();
    } catch (error) {
      setStatus(error?.message || 'Chat could not be deleted.');
    }
  }

  function row(chat) {
    const wrap = document.createElement('div');
    wrap.className = 'ch-row';

    const text = document.createElement('span');
    text.className = 'ch-row-text';

    const title = document.createElement('b');
    // The conversation's own title, which the account stores and the old screen
    // never read - it fell back to the word "Conversation" on every row.
    title.textContent = chat.title || 'New chat';

    const meta = document.createElement('small');
    meta.textContent = metaLine(chat);

    text.appendChild(title);
    text.appendChild(meta);

    const more = document.createElement('button');
    more.type = 'button';
    more.className = 'ch-more';
    more.setAttribute('aria-label', 'Actions for ' + title.textContent);
    more.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"'
      + ' stroke-width="2" stroke-linecap="round" aria-hidden="true">'
      + '<circle cx="5.5" cy="12" r="1.3"></circle>'
      + '<circle cx="12" cy="12" r="1.3"></circle>'
      + '<circle cx="18.5" cy="12" r="1.3"></circle></svg>';

    const menu = document.createElement('div');
    menu.className = 'ch-menu';
    menu.hidden = true;

    const open = document.createElement('button');
    open.type = 'button';
    open.textContent = 'Open';
    open.addEventListener('click', () => {
      closeMenus();
      void openChat(chat.id);
    });

    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'danger';
    remove.textContent = 'Delete';
    remove.addEventListener('click', () => {
      closeMenus();
      void removeChat(chat.id);
    });

    menu.appendChild(open);
    menu.appendChild(remove);

    more.addEventListener('click', event => {
      event.stopPropagation();
      const wasOpen = !menu.hidden;
      closeMenus();
      menu.hidden = wasOpen;
    });

    wrap.appendChild(text);
    wrap.appendChild(more);
    wrap.appendChild(menu);
    return wrap;
  }

  async function load() {
    setStatus('Loading chats…');

    try {
      // listConversations is the counterpart of the app's loadConversations: it
      // carries the title and the size, which listChats never did.
      const chats = (await window.IndooneCloudChat?.listConversations?.()) || [];

      if (list) {
        list.textContent = '';

        if (!chats.length) {
          const empty = document.createElement('p');
          empty.className = 'menu-feature-note';
          empty.textContent = 'No saved chats yet.';
          list.appendChild(empty);
        } else {
          chats.forEach(chat => list.appendChild(row(chat)));
        }
      }

      if (count) {
        if (chats.length) {
          count.textContent = chats.length + (chats.length === 1 ? ' chat' : ' chats');
          count.removeAttribute('hidden');
        } else {
          count.setAttribute('hidden', '');
        }
      }

      setStatus('');
    } catch (error) {
      setStatus(error?.message || 'Chat history could not be loaded right now.');
    }
  }

  // A tap anywhere else closes the actions menu.
  document.addEventListener('click', closeMenus);

  void load();
};
