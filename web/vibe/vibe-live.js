// The live Vibe session in the browser - the counterpart of VibeClient.kt and
// VibeAudioEngine.kt together.
//
// The app opens wss://<backend>/api/vibe/session, streams raw PCM audio up and
// plays raw PCM audio back. This does the same, so the web runs the same live
// session against the same Gemini Live model, rather than turning speech into
// text and back.
//
// The one difference the browser forces: it cannot set a header on a WebSocket,
// so the token goes as the opening message instead of in Authorization. The
// backend accepts either.

(() => {
  // One place, not five: see /config.js. A page can override it before this loads.
    // config.js owns the address, and this is read when a request is made rather than when the
  // file loads: the modules load in a fixed order, and a copy captured at load time is how the
  // old host survived in five files after the shared one was fixed.
  function backendBase() {
    const value = String(window.SANKALP_BACKEND || '').replace(/\/+$/, '');

    if (!value) {
      console.error('Sankalp: config.js did not load, so the backend address is unknown.');
    }

    return value;
  }
  const SESSION_PATH = '/api/vibe/session';
  const INPUT_RATE = 16000;
  const OUTPUT_RATE = 24000;
  // How far ahead the reply is scheduled, so a late chunk does not leave a gap.
  const PLAYBACK_LEAD_SECONDS = 0.12;
  const CAPTURE_MODULE = 'vibe/vibe-capture.js?v=20261018b';

  let socket = null;
  let captureContext = null;
  let captureNode = null;
  let captureStream = null;
  let captureSink = null;
  let playbackContext = null;
  let playbackGain = null;
  let speakerAudible = true;
  let playHead = 0;
  let closing = false;
  // True while the model's voice is coming out of the speaker. The microphone
  // must not be heard during that, or it picks up the reply and answers it -
  // which is how the screen ended up in a loop, talking to itself.
  let speaking = false;
  let quietTimer = null;

  function backendBase() {
    return window.IndooneChatApi?.backendUrl || backendBase();
  }

  function sessionUrl() {
    const base = backendBase();
    const ws = base.replace(/^https:\/\//, 'wss://').replace(/^http:\/\//, 'ws://');

    return `${ws}${SESSION_PATH}`;
  }

  async function authToken() {
    const user = window.firebase?.auth?.().currentUser || null;

    if (!user) {
      throw new Error('Please sign in again.');
    }

    return user.getIdToken(false);
  }

  function base64From(bytes) {
    let binary = '';
    const chunk = 0x8000;

    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }

    return btoa(binary);
  }

  function bytesFrom(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);

    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }

    return bytes;
  }

  // ---------------------------------------------------------------- microphone

  async function startCapture(onFrame) {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('This browser cannot reach the microphone.');
    }

    captureStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    });

    captureContext = new AudioContext();

    if (captureContext.state === 'suspended') {
      await captureContext.resume();
    }

    await captureContext.audioWorklet.addModule(
      CAPTURE_MODULE
    );

    const source = captureContext.createMediaStreamSource(captureStream);

    captureNode = new AudioWorkletNode(captureContext, 'vibe-capture', {
      numberOfInputs: 1,
      numberOfOutputs: 1
    });

    captureNode.port.onmessage = event => {
      const pcm = new Uint8Array(event.data);

      if (pcm.length && socket?.readyState === WebSocket.OPEN) {
        onFrame(pcm);
      }
    };

    // The browser pulls its audio graph from the speaker, and only asks a node
    // to process if there is a path from it to the speaker. A capture node with
    // nothing downstream is never run at all - so no audio was ever sent, and
    // the session sat there waiting. This gives it that path, at zero volume,
    // so it runs without the microphone being played back at you.
    const silence = captureContext.createGain();
    silence.gain.value = 0;

    source.connect(captureNode);
    captureNode.connect(silence);
    silence.connect(captureContext.destination);

    captureSink = silence;
  }

  function setSpeaking(speaking) {
    captureNode?.port?.postMessage({ speaking });
  }

  // ------------------------------------------------------------------ playback

  function ensurePlayback() {
    if (!playbackContext) {
      playbackContext = new AudioContext({ sampleRate: OUTPUT_RATE });
      playHead = playbackContext.currentTime;

      playbackGain = playbackContext.createGain();
      playbackGain.gain.value = speakerAudible ? 1 : 0;
      playbackGain.connect(playbackContext.destination);
    }

    if (playbackContext.state === 'suspended') {
      void playbackContext.resume();
    }

    return playbackContext;
  }

  // Hold the microphone while the model talks, and let it go once the queue
  // has drained. The app's audio engine does the same.
  function holdMicrophone() {
    speaking = true;
    setSpeaking(false);

    if (quietTimer) {
      clearTimeout(quietTimer);
      quietTimer = null;
    }
  }

  function releaseMicrophoneSoon() {
    if (quietTimer) {
      clearTimeout(quietTimer);
    }

    quietTimer = setTimeout(() => {
      const context = playbackContext;

      if (!context || context.currentTime >= playHead - 0.05) {
        speaking = false;
        setSpeaking(true);
      } else {
        releaseMicrophoneSoon();
      }
    }, 250);
  }

  function playPcm(bytes) {
    const context = ensurePlayback();
    const samples = new Int16Array(
      bytes.buffer,
      bytes.byteOffset,
      Math.floor(bytes.length / 2)
    );

    if (!samples.length) {
      return;
    }

    // The browser is free to ignore the rate we asked for. Use the one it
    // actually gave us, so the voice comes out at the right speed.
    const rate = context.sampleRate || OUTPUT_RATE;
    const buffer = context.createBuffer(1, samples.length, rate);
    const channel = buffer.getChannelData(0);

    for (let i = 0; i < samples.length; i += 1) {
      channel[i] = samples[i] / 0x8000;
    }

    const source = context.createBufferSource();
    source.buffer = buffer;
    source.connect(playbackGain || context.destination);

    const now = context.currentTime;

    // Keep a little lead. A chunk that arrives a moment late would otherwise
    // leave a hole where it should have been, and the voice came out in pieces.
    // The lead is small enough not to be heard as a delay.
    const lead = now + PLAYBACK_LEAD_SECONDS;

    if (playHead < lead) {
      playHead = lead;
    }

    source.start(playHead);
    playHead += buffer.duration;

    holdMicrophone();
    releaseMicrophoneSoon();
  }

  function setSpeaker(on) {
    speakerAudible = on !== false;

    if (playbackContext) {
      playbackGain?.gain?.setValueAtTime(
        speakerAudible ? 1 : 0,
        playbackContext.currentTime
      );
    }
  }

  function stopPlayback() {
    if (playbackContext) {
      void playbackContext.close();
      playbackContext = null;
    }

    playbackGain = null;

    playHead = 0;
    speaking = false;

    if (quietTimer) {
      clearTimeout(quietTimer);
      quietTimer = null;
    }

    setSpeaking(true);
  }

  // ------------------------------------------------------------------- session

  async function open(onEvent, onFailure, onPermissionDenied) {
    closing = false;

    let token;

    try {
      token = await authToken();
    } catch (error) {
      onFailure?.(error?.message || 'Please sign in again.');
      return;
    }

    // No subprotocol. The app names its protocol in a header, and a browser
    // cannot set one - but it can name a subprotocol, and that is not the same
    // thing. If the server does not echo the subprotocol back, the browser
    // closes the socket itself, which is what "The Vibe session ended." was.
    socket = new WebSocket(sessionUrl());

    socket.onopen = () => {
      socket.send(JSON.stringify({ type: 'auth', token }));
    };

    socket.onmessage = event => {
      let message = null;

      try {
        message = JSON.parse(event.data);
      } catch {
        return;
      }

      void handle(message, onEvent, onFailure);
    };

    socket.onerror = () => {
      if (!closing) {
        onFailure?.('Vibe could not reach the live voice service.');
      }
    };

    socket.onclose = event => {
      if (closing) {
        return;
      }

      // 1008 is the server refusing the token; 1006 is the socket dropping
      // before the server said anything.
      const reason = event?.code === 1008
        ? 'The Vibe service refused the sign-in.'
        : event?.code === 1006
          ? 'The Vibe service could not be reached.'
          : 'The Vibe session ended.';

      onFailure?.(reason);
    };
  }

  async function handle(message, onEvent, onFailure) {
    switch (message?.type) {
      case 'ready':
        onEvent?.({ type: 'ready', capabilities: message.capabilities });

        try {
          await startCapture(pcm => {
            socket.send(
              JSON.stringify({
                type: 'audio',
                audio_base64: base64From(pcm),
                mime_type: `audio/pcm;rate=${INPUT_RATE}`
              })
            );
          });
        } catch (error) {
          // VibeScreen.kt treats a refused microphone as its own state, not as a
          // connection error: it shows "Microphone permission required" on the
          // status line and "Microphone permission is required for Vibe." in the
          // transcript. A refusal arrives here as NotAllowedError (or
          // SecurityError when the page itself is not allowed to ask), so it is
          // handed to its own callback rather than the generic failure.
          if (error?.name === 'NotAllowedError' || error?.name === 'SecurityError') {
            onPermissionDenied?.();
            return;
          }

          onFailure?.(
            error?.message || 'This browser would not give the microphone.'
          );
        }

        return;

      case 'audio':
        if (typeof message.audio_base64 === 'string') {
          playPcm(bytesFrom(message.audio_base64));
        }

        return;

      case 'transcript':
        onEvent?.({
          type: 'transcript',
          role: message.role,
          text: message.text
        });
        return;

      case 'status':
        onEvent?.({
          type: 'status',
          interaction: message.interaction_status
        });
        return;

      case 'interrupted':
        stopPlayback();
        onEvent?.({ type: 'interrupted' });
        return;


      case 'error':
        onFailure?.(message.detail || 'The Vibe service reported an error.');
        return;

      default:
        return;
    }
  }

  function sendText(text) {
    if (socket?.readyState === WebSocket.OPEN && String(text).trim()) {
      socket.send(JSON.stringify({ type: 'text', text: String(text).trim() }));
    }
  }

  function close() {
    closing = true;

    try {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'stop' }));
      }
    } catch {
      // nothing to do - the socket is going away regardless
    }

    try {
      captureNode?.disconnect();
      captureStream?.getTracks?.().forEach(track => track.stop());
      void captureContext?.close();
    } catch {
      // same
    }

    captureNode = null;
    captureContext = null;
    captureStream = null;

    stopPlayback();

    try {
      socket?.close(1000, 'Vibe ended');
    } catch {
      // same
    }

    socket = null;
  }

  window.IndooneVibeLive = {
    open,
    close,
    sendText,
    setSpeaking,
    setSpeaker,
    stopPlayback,
    isOpen: () => socket?.readyState === WebSocket.OPEN,
    sessionUrl
  };
})();
