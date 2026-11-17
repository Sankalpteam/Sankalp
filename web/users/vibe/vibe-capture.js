// The audio worklet that turns the microphone into what Vibe expects.
//
// The app sends raw PCM, 16-bit, 16000 samples a second, as
//   {"type":"audio","audio_base64":"...","mime_type":"audio/pcm;rate=16000"}
// The microphone runs at the browser's rate - usually 48000 - so this reduces
// it to 16000 and hands back signed 16-bit samples, in blocks small enough to
// travel as they are spoken.

const TARGET_RATE = 16000;
const FRAME_SAMPLES = 1600; // a tenth of a second at 16000

class VibeCapture extends AudioWorkletProcessor {
  constructor() {
    super();

    this.ratio = sampleRate / TARGET_RATE;
    this.position = 0;
    this.pending = new Float32Array(FRAME_SAMPLES);
    this.pendingLength = 0;
    this.speaking = true;

    // The screen tells the worklet to hold its peace while the model is
    // talking, so the microphone does not hear the reply and answer itself.
    this.port.onmessage = event => {
      this.speaking = event.data?.speaking !== false;

      if (!this.speaking) {
        this.pendingLength = 0;
      }
    };
  }

  // Average the samples that fall between two output samples, rather than
  // dropping the ones between. Dropping them folds the high frequencies back
  // down as noise, and speech suffers for it.
  resample(input) {
    const output = new Float32Array(
      Math.floor((input.length - this.position) / this.ratio) + 1
    );
    let written = 0;

    while (this.position < input.length) {
      const start = Math.floor(this.position);
      const end = Math.min(Math.floor(this.position + this.ratio), input.length);
      let total = 0;
      let count = 0;

      for (let i = start; i < end; i += 1) {
        total += input[i];
        count += 1;
      }

      if (count === 0) {
        break;
      }

      output[written] = total / count;
      written += 1;
      this.position += this.ratio;
    }

    this.position -= input.length;

    return output.subarray(0, written);
  }

  flush() {
    if (this.pendingLength === 0) {
      return;
    }

    const frame = this.pending.subarray(0, this.pendingLength);
    const pcm = new Int16Array(this.pendingLength);

    for (let i = 0; i < this.pendingLength; i += 1) {
      const clamped = Math.max(-1, Math.min(1, frame[i]));
      pcm[i] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff;
    }

    this.port.postMessage(pcm.buffer, [pcm.buffer]);
    this.pendingLength = 0;
  }

  process(inputs) {
    const channel = inputs[0]?.[0];

    if (!channel || !this.speaking) {
      return true;
    }

    const reduced = this.resample(channel);

    for (let i = 0; i < reduced.length; i += 1) {
      this.pending[this.pendingLength] = reduced[i];
      this.pendingLength += 1;

      if (this.pendingLength === FRAME_SAMPLES) {
        this.flush();
      }
    }

    return true;
  }
}

registerProcessor('vibe-capture', VibeCapture);
