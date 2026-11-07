// The chat home, the web counterpart of android/home/HomeScreen.kt.
(() => {
  const MODULES = [
    // chat-api.js was written but never listed here, so window.IndooneChatApi
    // was undefined and both the composer and Vibe threw the moment they tried
    // to send. It goes first, because the others call it.
    'home/message/chat-api.js?v=20261020a',
    'home/message/cloud-chat.js?v=20261020b',
    'home/message/chat-store.js?v=20261018b',
    // The in-flight record. It has to load with the store, before anything can send.
    'home/message/inflight.js?v=20261026a',
    'home/message/message.js?v=20261023a',
    'home/send/send.js?v=20261020b',
    'home/voice/voice.js?v=20261019f',
    'home/plus/plus.js?v=20261018b',
    'home/plus/camera/camera.js?v=20261018b',
    'home/plus/photos/photos.js?v=20261018b',
    'home/plus/files/files.js?v=20261018b',
    'vibe/vibe-live.js?v=20261023a',
    'vibe/vibe.js?v=20261023a'
  ];

  // The app alternates these two, every two seconds.
  // The panel's second line. The first
  // line now says what Indoone can actually do; "How can I help you today?"
  // was already implied by the panel asking what to do.
  const WELCOME_LINES = [
    'I can read a screenshot, plan a trip, draft a message or explain something dense.',
    'Ask me anything.'
  ];

  let ready = null;
  let welcomeTimer = null;
  let welcomeIndex = 0;

  function loadScript(src) {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = src;
      script.onload = resolve;
      script.onerror = () => reject(new Error(`Could not load ${src}`));
      document.head.appendChild(script);
    });
  }

  function loadModules() {
    if (!ready) {
      ready = Promise.all(MODULES.map(loadScript));
    }

    return ready;
  }

  function showHistorySpinner(on) {
    const list = document.getElementById('chatList');
    const welcome = document.getElementById('chatWelcome');

    if (!list) return;

    if (on) {
      welcome?.setAttribute('hidden', '');
      list.textContent = '';
      const row = document.createElement('div');
      row.className = 'chat-loading';
      row.appendChild(document.createElement('span'));
      list.appendChild(row);
    } else {
      list.textContent = '';
    }
  }

  function scrollToBottom() {
    const scroller = document.getElementById('chatScroll');

    if (scroller) {
      scroller.scrollTop = scroller.scrollHeight;
    }
  }

  function paintWelcome() {
    const line = document.getElementById('welcomeLine');

    if (line) {
      line.textContent = WELCOME_LINES[welcomeIndex % WELCOME_LINES.length];
    }
  }

  function startWelcomeLoop() {
    if (welcomeTimer) {
      return;
    }

    welcomeTimer = setInterval(() => {
      welcomeIndex += 1;
      paintWelcome();
    }, 2000);
  }

  function stopWelcomeLoop() {
    if (welcomeTimer) {
      clearInterval(welcomeTimer);
      welcomeTimer = null;
    }
  }

  // The app's welcome says a flat "Hi! 👋" to everyone. The reply already
  // greets you by name - the profile in users/<uid>/profile carries one - and
  // the greeting below puts it on the empty screen too, with the hour.
  function partOfDay() {
    // The hour in Kolkata, not in whatever zone the browser thinks it is in. A device set
    // elsewhere was greeted with "Good evening" at ten in the morning, which is the same
    // mistake the dates had.
    const hour = Number(
      new Date().toLocaleString('en-GB', { ...IST, hour: '2-digit', hour12: false }).slice(0, 2)
    );

    if (hour < 12) {
      return 'Good morning';
    }

    if (hour < 17) {
      return 'Good afternoon';
    }

    return 'Good evening';
  }

  async function paintGreeting() {
    const title = document.getElementById('welcomeTitle');

    if (!title) {
      return;
    }

    let name = '';

    try {
      const profile = await window.IndooneUserData?.readProfile?.();
      name = String(profile?.name || '').trim();
    } catch (error) {
      // Not signed in, or the profile is unreachable. The greeting still works.
      name = '';
    }

    // Built with textContent, never innerHTML: the name comes from the account.
    title.textContent = '';

    if (name) {
      const greeting = document.createElement('span');
      greeting.textContent = partOfDay() + ', ';

      const who = document.createElement('span');
      who.className = 'welcome-name';
      who.textContent = name;

      title.append(greeting, who, document.createTextNode(' 👋'));
    } else {
      const greeting = document.createElement('span');
      greeting.textContent = partOfDay() + ' 👋';
      title.appendChild(greeting);
    }
  }

  const IST = { timeZone: 'Asia/Kolkata' };

  function whenLabel(millis) {
    if (!millis) {
      return '';
    }

    const then = new Date(Number(millis));

    // Asia/Kolkata, always - the same zone the app formats in. Using the browser's own zone
    // meant a device set to another part of the world showed a different day for the same
    // message, which reads as the wrong date rather than as a timezone.
    const sameDay =
      then.toLocaleDateString(IST, { day: '2-digit', month: '2-digit', year: 'numeric' }) ===
      new Date().toLocaleDateString(IST, { day: '2-digit', month: '2-digit', year: 'numeric' });

    if (sameDay) {
      return then.toLocaleTimeString(IST, { hour: '2-digit', minute: '2-digit' });
    }

    return then.toLocaleDateString(IST, { day: 'numeric', month: 'short', year: 'numeric' });
  }

  async function openResume(conversationId) {
    try {
      const conversation = await window.IndooneCloudChat?.loadConversation?.(
        conversationId
      );

      if (conversation) {
        window.IndooneChatStore?.setConversationId?.(conversationId);
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

    window.IndooneChatHome?.refresh?.();
  }

  // "Pick up where you left off" - the two most recent conversations, read
  // through the same list Chat History uses (IndooneCloudChat
  // .listConversations, the web counterpart of the app's loadConversations).
  async function loadResume() {
    const wrap = document.getElementById('welcomeResume');
    const list = document.getElementById('welcomeResumeList');

    if (!wrap || !list) {
      return;
    }

    let chats = [];

    try {
      chats = (await window.IndooneCloudChat?.listConversations?.(5)) || [];
    } catch (error) {
      chats = [];
    }

    // The thread already on screen is not something to go back to.
    const currentId = window.IndooneChatStore?.getConversationId?.();
    chats = chats.filter(chat => chat.id !== currentId).slice(0, 2);

    list.textContent = '';

    if (!chats.length) {
      wrap.setAttribute('hidden', '');
      return;
    }

    chats.forEach(chat => {
      const row = document.createElement('button');
      row.type = 'button';
      row.className = 'welcome-resume-row';

      const title = document.createElement('b');
      title.textContent = chat.title;

      const when = document.createElement('span');
      when.textContent = whenLabel(chat.updatedAt);

      row.append(title, when);
      row.addEventListener('click', () => void openResume(chat.id));
      list.appendChild(row);
    });

    wrap.removeAttribute('hidden');
  }

  function paintThread() {
    const list = document.getElementById('chatList');
    const store = window.IndooneChatStore;
    const welcome = document.getElementById('chatWelcome');
    const messages = store ? store.getMessages() : [];

    window.IndooneMessages?.render(list, messages, null);
    welcome?.toggleAttribute('hidden', messages.length > 0);
    scrollToBottom();

    if (messages.length) {
      stopWelcomeLoop();
    } else {
      startWelcomeLoop();
    }
  }

  function bindQuickPrompts() {
    document.querySelectorAll('.quick-chip').forEach(chip => {
      if (chip.dataset.promptBound === 'true') {
        return;
      }

      chip.dataset.promptBound = 'true';

      chip.addEventListener('click', () => {
        const input = document.getElementById('chatInput');

        if (input) {
          input.value = chip.dataset.prompt || chip.textContent.trim();
          input.dispatchEvent(new Event('input', { bubbles: true }));
          input.focus();
        }
      });
    });
  }

  // The app shows Send only when there is text or an attachment. Vibe used to
  // take that spot when the box was empty; it lives in the navigation now, so
  // the spot simply stays empty.
  function syncComposer() {
    const input = document.getElementById('chatInput');
    const send = document.getElementById('sendBtn');
    const listening = document.getElementById('listeningCard');
    const mic = document.getElementById('voiceBtn');
    const pending = window.IndooneSend?.getPending?.() || [];
    const hasText = Boolean((input?.value || '').trim());
    const showSend = hasText || pending.length > 0;
    const isListening = Boolean(listening && !listening.hasAttribute('hidden'));

    if (send) {
      send.hidden = !showSend;
      send.disabled = isListening;
    }

    mic?.classList.toggle('listening', isListening);
  }

  function autoGrow(event) {
    const input = event.target;
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 120)}px`;
  }

  // home/home.js loads first and publishes IndooneHome with its own init, and
  // events.js calls that one. This file's init - the one that loads chat-api,
  // cloud-chat, chat-store and the rest of the chat - was never published and
  // never called, so none of those modules ever loaded. Nothing could send, and
  // nothing reached the history. It runs itself now.
  // A turn that was open when the tab was refreshed or closed comes back to the
  // composer. The text is not re-sent automatically: the lost request may have been a
  // GitHub write, and repeating it without being asked would repeat the side effect.
  //
  // Nothing is added to the thread. An assistant bubble would be a lie - the model never
  // answered - and a system bubble has no styling of its own, so the honest signal is
  // the text back in the box where the user left it.
  function restoreInflightTurn() {
    const record = window.IndooneInflight?.pending?.();

    if (!record) {
      return;
    }

    window.IndooneInflight?.finish?.();

    const input = document.getElementById('chatInput');

    if (input && !input.value.trim()) {
      input.value = record.text;
      // The composer grows with its content, and the send button enables on input.
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    // The text comes back; a File does not, because the browser gives no handle to it
    // once the document is gone. Saying so is the difference between a restore and a
    // silently incomplete message.
    if (record.hadAttachments) {
      window.toast?.('Message restored - please re-attach your file');
    }

    console.warn('Indoone: an unfinished message was restored to the composer.');
  }

  async function init() {
    const page = document.getElementById('homeContent');

    if (!page || page.dataset.ready === 'true') {
      return;
    }

    page.dataset.ready = 'true';

    try {
      await loadModules();
    } catch (error) {
      console.error('Indoone chat home could not load:', error);
      // Even then the composer must be in the right state: no account "+" left
      // on screen.
      syncComposer();
      return;
    }

    window.IndooneChatScroll = { toBottom: scrollToBottom };
    window.IndoonePlusSheet?.init(document.getElementById('plusSheet'));
    window.IndooneVibe?.init();

    bindQuickPrompts();
    paintWelcome();
    void paintGreeting();
    void loadResume();
    restoreInflightTurn();
    startWelcomeLoop();

    document
      .getElementById('plusBtn')
      ?.addEventListener('click', () => {
        const sheet = window.IndoonePlusSheet;

        if (sheet?.isOpen?.()) {
          sheet.close();
        } else {
          sheet?.open?.();
        }
      });

    document
      .getElementById('voiceBtn')
      ?.addEventListener('click', () => window.IndooneVoice?.toggle?.());

    document
      .getElementById('sendBtn')
      ?.addEventListener('click', () => window.IndooneSend?.send?.());

    const input = document.getElementById('chatInput');

    input?.addEventListener('input', event => {
      autoGrow(event);
      syncComposer();
    });

    input?.addEventListener('keydown', event => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        void window.IndooneSend?.send?.();
      }
    });

    // The app shows a spinner while it reads the saved conversation.
    showHistorySpinner(true);

    try {
      await window.IndooneChatStore?.load?.();
    } finally {
      showHistorySpinner(false);
    }

    paintThread();
    syncComposer();
  }

  function refresh() {
    paintThread();
    syncComposer();
  }

  window.IndooneChatHome = { init, refresh, syncComposer, scrollToBottom };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => { void init(); }, { once: true });
  } else {
    void init();
  }
})();
