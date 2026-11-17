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
      console.warn('Sankalp chat could not be opened:', error);
    }

    window.showHome?.();
  }

  async function removeChat(id) {
    try {
      // deleteConversation drops the conversation, its messages and the index -
      // the whole of it, which is what the app's delete does.
      await window.IndooneCloudChat?.deleteConversation?.(id);

      // If the conversation being deleted is the one the chat screen is holding, the screen
      // has to let go of it. Without this the next message is appended to a thread that no
      // longer exists and sent with its id, so the assistant answers the deleted question.
      if (window.IndooneChatStore?.getConversationId?.() === id) {
        window.IndooneChatStore?.clear?.();
        window.IndooneChatHome?.refresh?.();
      }

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

    function actionButton(label, extraClass, handler) {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = label;

      if (extraClass) {
        button.className = extraClass;
      }

      button.addEventListener('click', event => {
        event.stopPropagation();
        closeMenus();
        handler();
      });

      return button;
    }

    const open = actionButton('Open', '', () => void openChat(chat.id));
    const remove = actionButton('Delete', 'danger', () => void removeChat(chat.id));

    menu.appendChild(actionButton('Open', '', () => void openChat(chat.id)));
    menu.appendChild(actionButton('Delete', 'danger', () => void removeChat(chat.id)));

    // The same two actions, out in the open, for a pointer that has room for them.
    //
    // On a phone the ellipsis is right: the row is narrow and a delete button next to every
    // chat is a trap for a thumb. On a desktop there is room, and hiding two words behind a
    // menu that only appears on click is a step nobody asked for.
    const inline = document.createElement('span');
    inline.className = 'ch-actions';
    inline.appendChild(open);
    inline.appendChild(remove);

    more.addEventListener('click', event => {
      event.stopPropagation();
      const wasOpen = !menu.hidden;
      closeMenus();
      menu.hidden = wasOpen;
    });

    wrap.appendChild(text);
    wrap.appendChild(inline);
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
