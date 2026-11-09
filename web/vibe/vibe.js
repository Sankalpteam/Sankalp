// The Vibe screen - the counterpart of VibeScreen.kt.
//
// It runs the same live session the native app runs, through IndooneVibeLive:
// the same /api/vibe/session, the same Gemini Live model, raw audio both ways.
// The words on the screen are the app's own, read from VibeScreen.kt and
// VibeSessionService.kt:
//
//   Connecting      before the session is up
//   Thinking…       while the model is working on an answer
//   Listening       when the microphone is open and the model is not speaking
//   Paused          when the microphone is muted
//   Disconnected    when the session ended
//   Connection error when it could not be reached
(() => {
  let screen = null;
  let orb = null;
  let state = null;
  let hint = null;
  let transcript = null;
  let mute = null;
  let muteIcon = null;
  let muteLabel = null;
  let end = null;
  let speaker = null;
  let speakerIcon = null;
  let speakerLabel = null;

  let connected = false;
  let closed = false;
  let listening = false;
  let speakerOn = true;
  let thinking = false;
  let userLine = '';

  const CONNECTING = 'Connecting';
  const THINKING = 'Thinking…';
  const LISTENING = 'Listening';
  const PAUSED = 'Paused';
  const PLACEHOLDER = 'Your live conversation will appear here.';

  function setState(text) {
    if (state) {
      state.textContent = text;
    }
  }

  function setHint(text) {
    if (hint) {
      hint.textContent = text;
    }
  }

  function setTranscript(text) {
    if (transcript) {
      transcript.textContent = text;
    }
  }

  function paintMute() {
    mute?.classList.toggle('on', listening);
    muteLabel && (muteLabel.textContent = listening ? 'Mute' : 'Unmute');

    // VibeScreen.kt uses Icons.Outlined.Mic while listening and MicOff when
    // muted. The slashed one was left on the button whatever the state, and
    // its slash is the line that looked like a mark over the microphone.
    if (muteIcon) {
      muteIcon.dataset.appIcon = listening ? 'mic' : 'micOff';

      // redraw, not apply. apply() skips anything it has already drawn, so
      // changing the name alone left the old icon in place - which is why the
      // button looked the same whether it was muted or not.
      window.IndooneIcons?.redraw?.(muteIcon);
    }
  }

  function paintSpeaker() {
    speaker?.classList.toggle('on', speakerOn);
    speakerLabel && (speakerLabel.textContent = speakerOn ? 'Speaker' : 'Muted');

    // VibeScreen.kt uses VolumeUp when the speaker is on and VolumeOff when it
    // is muted. The icon was left on volumeUp whatever the state.
    if (speakerIcon) {
      speakerIcon.dataset.appIcon = speakerOn ? 'volumeUp' : 'volumeOff';
      window.IndooneIcons?.redraw?.(speakerIcon);
    }
  }

  // VibeScreen.kt shows the connection state until it is Connected, and then
  // Listening or Paused.
  function paintState() {
    if (!connected) {
      setState(CONNECTING);
      return;
    }

    if (thinking) {
      setState(THINKING);
      return;
    }

    setState(listening ? LISTENING : PAUSED);
  }

  function onEvent(event) {
    switch (event.type) {
      case 'ready':
        connected = true;
        listening = true;
        paintMute();
        paintState();
        setHint('Speak naturally');
        return;

      case 'status':
        thinking = event.interaction === 'IN_PROGRESS';
        paintState();
        return;

      case 'transcript': {
        // VibeSessionService.kt stores a user line as "You: $text" and an
        // assistant line as "Sankalp: $text", so the transcript carries the
        // speaker's name.
        const line = String(event.text || '').trim();

        if (event.role === 'assistant') {
          setTranscript(`Sankalp: ${line}`);
        } else {
          userLine = line;
          setTranscript(`You: ${line}`);
        }

        return;
      }

      case 'interrupted':
        thinking = false;
        paintState();
        return;

      default:
        return;
    }
  }

  function onFailure(detail) {
    connected = false;
    listening = false;
    paintMute();
    setState('Connection error');
    setTranscript(detail || 'Vibe could not reach the live voice service.');
  }

  // VibeScreen.kt keeps a refused microphone apart from a connection error. When
  // permissionDenied is set it draws "Microphone permission required" on the
  // status line and "Microphone permission is required for Vibe." in the
  // transcript, and it is the same words here.
  function onPermissionDenied() {
    connected = false;
    listening = false;
    paintMute();
    setState('Microphone permission required');
    setTranscript('Microphone permission is required for Vibe.');
  }

  function open() {
    closed = false;
    connected = false;
    listening = false;
    thinking = false;
    userLine = '';
    speakerOn = true;

    screen?.removeAttribute('hidden');
    setTranscript(PLACEHOLDER);
    setState(CONNECTING);
    setHint('Speak naturally');
    paintMute();
    paintSpeaker();
    window.IndooneVibeLive?.setSpeaker?.(speakerOn);

    void window.IndooneVibeLive?.open?.(onEvent, onFailure, onPermissionDenied);
  }

  function close() {
    closed = true;
    connected = false;
    listening = false;
    thinking = false;

    window.IndooneVibeLive?.close?.();

    screen?.setAttribute('hidden', '');
    setState(PAUSED);
  }

  function toggleMic() {
    if (closed) {
      return;
    }

    listening = !listening;
    paintMute();

    // While muted the worklet stops sending, so the model hears nothing.
    window.IndooneVibeLive?.setSpeaking?.(listening);

    if (listening) {
      window.IndooneVibeLive?.stopPlayback?.();
    }

    paintState();
  }

  function init() {
    screen = document.getElementById('vibeScreen');
    orb = document.getElementById('vibeOrb');
    state = document.getElementById('vibeState');
    hint = document.getElementById('vibeHint');
    transcript = document.getElementById('vibeTranscript');
    mute = document.getElementById('vibeMute');
    muteIcon = document.getElementById('vibeMuteIcon');
    muteLabel = document.getElementById('vibeMuteLabel');
    end = document.getElementById('vibeEnd');
    speaker = document.getElementById('vibeSpeaker');
    speakerIcon = document.getElementById('vibeSpeakerIcon');
    speakerLabel = document.getElementById('vibeSpeakerLabel');

    mute?.addEventListener('click', toggleMic);
    // VibeScreen.kt's orb has no click handler - only the mic control toggles
    // the mic - so tapping the orb does nothing here either.

    speaker?.addEventListener('click', () => {
      speakerOn = !speakerOn;
      paintSpeaker();
      // and actually silence it, as the app does
      window.IndooneVibeLive?.setSpeaker?.(speakerOn);
    });

    end?.addEventListener('click', close);
  }

  window.IndooneVibe = { init, open, close };
})();
