// Renders the message list, the way the app draws it:
//   - an AI reply has the Indoone mark to its left
//   - a user reply is a violet bubble, right aligned
//   - sent photos stack, sent files show a badge
//   - a URL in a reply gets its own row with a copy button
//   - while a reply is coming, the mark sits next to a spinner and a label
(() => {
  const ACTIVITY_LABELS = {
    received: 'Received',
    thinking: 'Thinking…',
    preparing: 'Preparing your answer…'
  };

  // HomeScreen.kt: REPLY_LINK_PATTERN = Regex("""https?://\S+""") - an http or
  // https URL, running to the next space.
  const REPLY_LINK_PATTERN = /https?:\/\/\S+/g;

  function logo() {
    const mark = document.createElement('span');
    mark.className = 'chat-logo';
    mark.setAttribute('aria-hidden', 'true');
    return mark;
  }

  function isImage(item) {
    const file = item.file;
    const type = file?.type || item.type || '';
    return type.startsWith('image/');
  }

  // The app's splitReplyIntoParts: the prose, then one part per URL, then the
  // tail. The prose keeps its own line breaks, so the bubble's pre-wrap still
  // does its job.
  // [label](url) is a link the backend wrote on purpose - a repository list, for example.
  // It is matched before bare URLs so the label stays inline with its line instead of the
  // URL being pulled out into a row of its own and the line splitting in two.
  const MARKDOWN_LINK_PATTERN = /\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g;

  function replyParts(text) {
    const source = String(text || '');
    const parts = [];

    // Both patterns are matched in one pass, in the order they appear.
    //
    // They used to run one after the other: every [label](url) was found first and the text
    // before the first one was pushed as a single prose run - so a reply that ended with a
    // markdown link left every bare URL above it inline in the text, wrapped across the line
    // and with no copy button. The same reply without the markdown link rendered correctly,
    // which is why it looked like an intermittent display bug.
    const found = [];

    MARKDOWN_LINK_PATTERN.lastIndex = 0;
    let match;
    while ((match = MARKDOWN_LINK_PATTERN.exec(source)) !== null) {
      found.push({ start: match.index, end: match.index + match[0].length, label: match[1], url: match[2] });
    }

    REPLY_LINK_PATTERN.lastIndex = 0;
    while ((match = REPLY_LINK_PATTERN.exec(source)) !== null) {
      const start = match.index;
      const end = start + match[0].length;
      // a URL that is already the target of a markdown link is not a second part
      if (found.some(item => start >= item.start && start < item.end)) continue;
      found.push({ start, end, url: match[0] });
    }

    found.sort((a, b) => a.start - b.start);

    let last = 0;

    found.forEach(item => {
      if (item.start > last) {
        parts.push({ prose: source.slice(last, item.start) });
      }

      parts.push(item.label ? { label: item.label, url: item.url } : { url: item.url });
      last = item.end;
    });

    if (last < source.length) {
      parts.push({ prose: source.slice(last) });
    }

    return parts;
  }

  function copyText(text) {
    if (navigator.clipboard?.writeText) {
      return navigator.clipboard.writeText(text);
    }

    // A tab served over plain http has no clipboard API, so fall back to the
    // old selection trick rather than leaving the button doing nothing.
    return new Promise((resolve, reject) => {
      const field = document.createElement('textarea');
      field.value = text;
      field.setAttribute('readonly', '');
      field.style.position = 'fixed';
      field.style.opacity = '0';
      document.body.appendChild(field);
      field.select();

      try {
        document.execCommand('copy') ? resolve() : reject(new Error('copy refused'));
      } catch (error) {
        reject(error);
      } finally {
        field.remove();
      }
    });
  }

  // The app's ReplyLinkRow: the URL in violet on one line, ellipsis if it is
  // long, and a copy button at the end of the row. The button puts the whole
  // URL on the clipboard and says so - a tap with no visible result reads as
  // though it did nothing.
  function linkRow(url, isFirst) {
    const row = document.createElement('div');
    row.className = isFirst ? 'chat-link-row' : 'chat-link-row chat-gap';

    const link = document.createElement('span');
    link.className = 'chat-link-url';
    link.textContent = url;

    const copy = document.createElement('button');
    copy.type = 'button';
    copy.className = 'chat-link-copy';
    copy.setAttribute('aria-label', 'Copy link');
    copy.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"'
      + ' stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
      + '<rect x="9" y="9" width="11" height="11" rx="2"></rect>'
      + '<path d="M5.5 15H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v.5"></path></svg>';

    copy.addEventListener('click', () => {
      copyText(url)
        .then(() => window.toast?.('Link copied'))
        .catch(() => window.toast?.('Could not copy the link'));
    });

    row.appendChild(link);
    row.appendChild(copy);
    return row;
  }

  function sentAttachments(attachments) {
    const wrap = document.createElement('div');
    wrap.className = 'chat-attachments';

    attachments.forEach(item => {
      if (isImage(item) && item.url) {
        const image = document.createElement('img');
        image.className = 'chat-attachment-photo';
        image.src = item.url;
        image.alt = item.name || 'Sent photo';
        wrap.appendChild(image);
        return;
      }

      // HomeScreen.kt draws a sent file as the attachment's own name in a
      // violet pill - Surface(widthIn(max = 220.dp), RoundedCornerShape(9.dp),
      // color = 0xFF8055DC, border = 0xFFB69AEF) with white 11sp text on two
      // lines at most. The web used to show a 48px tile holding the file's
      // extension instead, which is not what the app shows.
      const pill = document.createElement('span');
      pill.className = 'chat-attachment-file';
      pill.textContent = item.name || 'Attachment';
      wrap.appendChild(pill);
    });

    return wrap;
  }

  // Copy, and Share where the browser offers it.
  //
  // Share is `navigator.share`, which phones have and desktop Chrome does not - so the
  // button is only added when the API exists, rather than shown and failing. Copy is
  // always there, and says so when it works: a tap with no visible result reads as a
  // broken button.
  // ---- icons ----------------------------------------------------------------
  // Text buttons made the row wide and shouty under every reply. A compact row of
  // icons aligned to the bubble reads as an affordance instead of a toolbar, and the
  // label still exists for a screen reader through aria-label and title.
  const ICON_PATHS = {
    copy:
      '<rect x="9" y="9" width="11" height="11" rx="2"></rect>' +
      '<path d="M5.5 15H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v.5"></path>',
    share:
      '<path d="M12 15V4"></path><path d="m8 7 4-3 4 3"></path>' +
      '<path d="M5 13v6h14v-6"></path>',
    speaker:
      '<path d="M4 9.5h3l4-3.5v12l-4-3.5H4z"></path>' +
      '<path d="M15.5 9a4 4 0 0 1 0 6"></path>',
    stop: '<rect x="7" y="7" width="10" height="10" rx="2"></rect>',
    thumbUp:
      '<path d="M7 11v8H4v-8z"></path>' +
      '<path d="M7 11l4-7a2 2 0 0 1 2 2v3h4a2 2 0 0 1 2 2l-1.5 6H7"></path>',
    thumbDown:
      '<path d="M7 13V5H4v8z"></path>' +
      '<path d="M7 13l4 7a2 2 0 0 0 2-2v-3h4a2 2 0 0 0 2-2l-1.5-6H7"></path>',
    retry:
      '<path d="M4 12a8 8 0 1 1 2.5 5.8"></path><path d="M4 20v-5h5"></path>',
    edit:
      '<path d="M4 20h4l10-10a2.5 2.5 0 0 0-4-4L4 16z"></path>' +
      '<path d="M13.5 6.5 17.5 10.5"></path>'
  };

  function icon(name) {
    return (
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"' +
      ' stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      (ICON_PATHS[name] || '') +
      '</svg>'
    );
  }

  function iconButton(name, label) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'chat-action';
    button.setAttribute('aria-label', label);
    button.setAttribute('title', label);
    button.innerHTML = icon(name);
    return button;
  }

  // ---- speech ---------------------------------------------------------------
  // One utterance at a time. Starting a second while the first is speaking talks over
  // itself, so whatever is playing is cancelled first, and the button that started it
  // goes back to "Listen".
  let speaking = null;

  function stopSpeaking() {
    if (!('speechSynthesis' in window)) {
      return;
    }

    window.speechSynthesis.cancel();

    const previous = speaking;
    speaking = null;

    if (previous && previous.button) {
      setListenState(previous.button, false);
    }
  }

  // A voice chosen for the assistant rather than the browser's default, which on most
  // systems is the same robotic one every app uses. Preference order: an Indian English
  // voice, then any Google English voice, then the first English voice the platform has.
  // A genuinely branded character needs a cloud speech service; this is the best a
  // system engine can do, and it is honest about which one it picked.
  function preferredVoice() {
    if (!('speechSynthesis' in window)) {
      return null;
    }

    const voices = window.speechSynthesis.getVoices() || [];

    if (!voices.length) {
      return null;
    }

    const english = voices.filter(voice => /^en(-|_|$)/i.test(voice.lang || ''));

    return (
      english.find(voice => /en[-_]IN/i.test(voice.lang || '')) ||
      english.find(voice => /google/i.test(voice.name || '')) ||
      english.find(voice => /natural|neural|enhanced|premium/i.test(voice.name || '')) ||
      english[0] ||
      voices[0]
    );
  }

  function toggleSpeech(text, button) {
    if (!('speechSynthesis' in window)) {
      return;
    }

    if (speaking && speaking.text === text) {
      stopSpeaking();
      return;
    }

    stopSpeaking();

    const utterance = new SpeechSynthesisUtterance(text);
    const voice = preferredVoice();

    if (voice) {
      utterance.voice = voice;
      utterance.lang = voice.lang || 'en-IN';
    }

    // A shade slower and slightly lower than the platform default: the defaults read
    // like a screen reader, and a reply read at speed is hard to follow.
    utterance.rate = 0.98;
    utterance.pitch = 0.95;

    const entry = { text, button };

    const settle = () => {
      if (speaking === entry) {
        speaking = null;
        setListenState(button, false);
      }
    };

    utterance.onend = settle;
    utterance.onerror = settle;

    speaking = entry;
    setListenState(button, true);
    window.speechSynthesis.speak(utterance);
  }

  // ---- feedback -------------------------------------------------------------
  // Local only for now. There is no endpoint that accepts a rating, and a button that
  // pretends to send one is worse than no button: the ratings are kept here so they can
  // go to the backend the day it has somewhere to put them.
  const FEEDBACK_KEY = 'indoone_feedback_v1';

  function readFeedback() {
    try {
      const parsed = JSON.parse(localStorage.getItem(FEEDBACK_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      return [];
    }
  }

  function recordFeedback(text, rating) {
    const kept = readFeedback().filter(item => item && item.text !== text);
    kept.push({ text: String(text).slice(0, 2000), rating, at: Date.now() });

    try {
      localStorage.setItem(FEEDBACK_KEY, JSON.stringify(kept.slice(-200)));
    } catch (error) {
      // A full quota must not break the thread.
    }
  }

  function ratingFor(text) {
    const match = readFeedback().find(item => item && item.text === text);
    return match ? match.rating : '';
  }

  // ---- retry ----------------------------------------------------------------
  // Re-ask the question behind the last reply. The trailing exchange is removed first -
  // the reply and the question it answered - because send() adds the question back. Both
  // are in the store, so the thread does not grow a duplicate turn.
  function retryLast(messages) {
    const store = window.IndooneChatStore;
    const send = window.IndooneSend;

    if (!store || !send || !Array.isArray(messages) || !messages.length) {
      return;
    }

    let questionIndex = -1;

    for (let index = messages.length - 1; index >= 0; index -= 1) {
      if (messages[index].mine) {
        questionIndex = index;
        break;
      }
    }

    if (questionIndex < 0) {
      return;
    }

    const question = messages[questionIndex];
    const currentRetryCount = Number(question.retryCount || 0);

    if (currentRetryCount >= 2) {
      return;
    }

    const next = messages.slice(0, questionIndex + 1);
    const updatedQuestion = { ...question, retryCount: currentRetryCount + 1 };
    next[next.length - 1] = updatedQuestion;
    store.replace(next);

    const input = document.getElementById('chatInput');

    if (input) {
      input.value = String(question.text || '').trim();
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    send.send();
  }

  function setListenState(button, isSpeaking) {
    button.innerHTML = icon(isSpeaking ? 'stop' : 'speaker');
    const label = isSpeaking ? 'Stop reading' : 'Listen to this reply';
    button.setAttribute('aria-label', label);
    button.setAttribute('title', label);
  }

  // Editing a prompt means putting it back where it came from: the composer. The sent
  // message stays in the thread, because the reply below it answered that text - silently
  // rewriting history would leave an answer to a question that no longer exists.
  // Editing replaces the message. The thread is cut back to just before it - the old
  // prompt and the answer it got - and the text goes into the composer. Sending then
  // puts the edited prompt where the old one was, instead of appending a second copy
  // underneath an answer to a question the user has just changed.
  function editPrompt(text, index, messages) {
    const input = document.getElementById('chatInput');

    if (!input) {
      return;
    }

    if (Array.isArray(messages) && index >= 0 && index < messages.length) {
      window.IndooneChatStore?.replace(messages.slice(0, index));
    }

    input.value = text;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.focus();
    window.toast?.('Edit your message, then send');
  }

  // A compact row under the bubble.
  //
  // A reply carries copy, share, listen, a rating and - on the latest one - retry. The
  // user's own message carries one thing: edit. Copy on a reply copies the pair, because
  // an answer on its own is usually meaningless out of context.
  function actionRow(message, options) {
    const text = String(message.text || '').trim();

    if (!text) {
      return null;
    }

    const row = document.createElement('div');
    row.className = 'chat-actions';

    if (message.mine) {
      const edit = iconButton('edit', 'Edit this message');
      edit.addEventListener('click', () => (options && options.onEdit ? options.onEdit() : editPrompt(text)));
      row.appendChild(edit);
      return row;
    }

    const pair = (options && options.pairText) || text;

    const copy = iconButton('copy', 'Copy this exchange');
    copy.addEventListener('click', () => {
      copyText(pair)
        .then(() => window.toast?.('Copied'))
        .catch(() => window.toast?.('Could not copy'));
    });
    row.appendChild(copy);

    if (typeof navigator.share === 'function') {
      const share = iconButton('share', 'Share this exchange');
      share.addEventListener('click', () => {
        // A dismissed share sheet rejects; that is not an error to report.
        Promise.resolve(navigator.share({ text: pair })).catch(() => {});
      });
      row.appendChild(share);
    }

    if ('speechSynthesis' in window) {
      const listen = iconButton('speaker', 'Listen to this reply');
      listen.addEventListener('click', () => toggleSpeech(text, listen));
      row.appendChild(listen);
    }

    const rating = ratingFor(text);

    ['up', 'down'].forEach(kind => {
      const label = kind === 'up' ? 'Good reply' : 'Bad reply';
      const thumb = iconButton(kind === 'up' ? 'thumbUp' : 'thumbDown', label);
      thumb.classList.add('chat-rating');

      if (rating === kind) {
        thumb.classList.add('is-on');
        thumb.setAttribute('aria-pressed', 'true');
      }

      thumb.addEventListener('click', () => {
        recordFeedback(text, kind);
        row.querySelectorAll('.chat-rating').forEach(node => {
          node.classList.remove('is-on');
          node.setAttribute('aria-pressed', 'false');
        });
        thumb.classList.add('is-on');
        thumb.setAttribute('aria-pressed', 'true');
      });

      row.appendChild(thumb);
    });

    if (options && options.isLastAssistant && options.onRetry) {
      const retry = iconButton('retry', 'Ask again');
      const allowed = Boolean(options.retryAllowed);
      retry.disabled = !allowed;
      retry.hidden = !allowed;
      retry.setAttribute('aria-disabled', String(!allowed));
      if (allowed) {
        retry.addEventListener('click', options.onRetry);
      }
      row.appendChild(retry);
    }

    return row;
  }

  // A follow-up the backend offered with this reply: a held write's Confirm/Cancel, or
  // the one next step a list makes obvious. Pressing one sends exactly its action text -
  // for Confirm that is the word the reply used to ask the user to type, so a button and
  // typing cannot diverge.
  function chipsRow(chips) {
    if (!Array.isArray(chips) || !chips.length) {
      return null;
    }

    const row = document.createElement('div');
    row.className = 'chat-chips';

    chips.forEach(chip => {
      const label = String((chip && chip.label) || '').trim();
      const action = String((chip && chip.action) || '').trim();

      if (!label || !action) {
        return;
      }

      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'chat-chip';
      button.textContent = label;

      button.addEventListener('click', () => {
        const input = document.getElementById('chatInput');

        if (!input) {
          return;
        }

        input.value = action;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        window.IndooneSend?.send?.();
      });

      row.appendChild(button);
    });

    return row.childElementCount ? row : null;
  }

  function bubble(message, options) {
    const row = document.createElement('div');
    row.className = message.mine ? 'chat-row mine' : 'chat-row theirs';

    if (!message.mine) {
      row.appendChild(logo());
    }

    const column = document.createElement('div');
    column.className = 'chat-column';

    if (message.attachments && message.attachments.length) {
      column.appendChild(sentAttachments(message.attachments));
    }

    if (message.text) {
      const body = document.createElement('div');
      body.className = 'chat-bubble';

      // A reply that carries links reads better as its prose and then one row
      // per link, each with a copy button, than as one run of text with the
      // URLs buried inside it.
      replyParts(message.text).forEach((part, index) => {
        if (part.url && part.label) {
          const link = document.createElement('a');
          link.className = 'chat-link';
          link.href = part.url;
          link.textContent = part.label;
          link.target = '_blank';
          link.rel = 'noopener noreferrer';
          body.appendChild(link);
          return;
        }

        if (part.url) {
          body.appendChild(linkRow(part.url, index === 0));
          return;
        }

        const text = document.createElement('span');
        text.className = index === 0 ? 'chat-text' : 'chat-text chat-gap';
        text.textContent = part.prose;
        body.appendChild(text);
      });

      column.appendChild(body);
    }

    const actions = actionRow(message, options);

    if (actions) {
      column.appendChild(actions);
    }

    const chips = chipsRow(message.chips);

    if (chips) {
      column.appendChild(chips);
    }

    row.appendChild(column);
    return row;
  }

  function activityBubble(state) {
    const row = document.createElement('div');
    row.className = 'chat-row theirs';
    row.appendChild(logo());

    const column = document.createElement('div');
    column.className = 'chat-column';

    const body = document.createElement('div');
    body.className = 'chat-bubble chat-activity';

    const spinner = document.createElement('span');
    spinner.className = 'chat-spinner';

    const label = document.createElement('span');
    label.textContent = ACTIVITY_LABELS[state] || ACTIVITY_LABELS.thinking;

    body.appendChild(spinner);
    body.appendChild(label);
    column.appendChild(body);
    row.appendChild(column);
    return row;
  }

  function render(list, messages, activity) {
    if (!list) return;

    list.textContent = '';

    const lastAssistant = messages.reduce(
      (found, message, index) => (message.mine ? found : index),
      -1
    );

    const lastQuestion = messages.reduce(
      (found, message, index) => (message.mine ? index : found),
      -1
    );
    const retryCount = Number(messages[lastQuestion]?.retryCount || 0);
    const retryAllowed = lastQuestion >= 0 && retryCount < 2;

    messages.forEach((message, index) => {
      // Copy and share carry the question with the answer: an answer on its own is
      // usually meaningless out of context, and pasting a pair back into a chat is what
      // people actually do with it.
      let question = '';

      if (!message.mine) {
        for (let before = index - 1; before >= 0; before -= 1) {
          if (messages[before].mine) {
            question = String(messages[before].text || '').trim();
            break;
          }
        }
      }

      const answer = String(message.text || '').trim();
      const pairText = question ? `User: ${question}\n\nAI: ${answer}` : answer;

      list.appendChild(
        bubble(message, {
          isLastAssistant: index === lastAssistant,
          retryAllowed,
          pairText,
          onRetry: () => retryLast(messages),
          onEdit: () => editPrompt(answer, index, messages)
        })
      );
    });

    if (activity) {
      list.appendChild(activityBubble(activity));
    }
  }

  window.IndooneMessages = { render };
})();
