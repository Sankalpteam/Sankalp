// Voice input. The app shows a red error line under the composer and a card
// while the mic is on, so this does the same instead of a passing toast.
(() => {
  const SpeechRecognition =
    window.SpeechRecognition || window.webkitSpeechRecognition;

  let recognition = null;
  let listening = false;
  let base = '';

  function card() {
    return document.getElementById('listeningCard');
  }

  function errorNode() {
    return document.getElementById('voiceError');
  }

  function setError(text) {
    const node = errorNode();

    if (!node) {
      return;
    }

    node.textContent = text || '';
    node.hidden = !text;
  }

  function paint() {
    const node = card();

    if (node) {
      node.hidden = !listening;
    }

    window.IndooneChatHome?.syncComposer?.();
  }

  function stop() {
    listening = false;
    paint();
    recognition?.stop?.();
  }

  function toggle() {
    if (listening) {
      stop();
      return;
    }

    start();
  }

  function start() {
    if (!SpeechRecognition) {
      setError('Speech recognition is not available on this device.');
      return;
    }

    setError('');

    const input = document.getElementById('chatInput');
    base = input?.value || '';

    recognition = new SpeechRecognition();
    recognition.lang = navigator.language || 'en-IN';
    recognition.continuous = false;
    recognition.interimResults = true;

    recognition.onresult = event => {
      let spoken = '';

      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        spoken += event.results[i][0].transcript;
      }

      if (input) {
        input.value = base
          ? `${base} ${spoken}`.trim()
          : spoken.trim();
        input.dispatchEvent(new Event('input', { bubbles: true }));
      }
    };

    recognition.onerror = event => {
      listening = false;
      paint();

      const messages = {
        'no-speech': 'No speech detected. Try again.',
        // HomeScreen.kt's ERROR_NO_MATCH line.
        'no-match': 'I could not hear that clearly. Try again.',
        'not-allowed': 'Microphone permission is required.',
        'service-not-allowed': 'Microphone permission is required.',
        'audio-capture': 'Microphone permission is required.'
      };

      setError(messages[event.error] || 'Voice input failed. Please try again.');
    };

    recognition.onend = () => {
      listening = false;
      paint();
    };

    listening = true;
    paint();
    recognition.start();
  }

  window.IndooneVoice = { start, stop, toggle, available: Boolean(SpeechRecognition) };
})();
