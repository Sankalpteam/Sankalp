// Camera: getUserMedia where the browser has it, and the plain file input as
// the fallback, which on a phone opens the camera anyway.
(() => {
  function fallback() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.capture = 'environment';

    input.addEventListener('change', () => {
      const file = (input.files || [])[0];

      if (file) {
        window.IndooneSend?.attach?.(file);
      }
    });

    input.click();
  }

  async function pick() {
    if (!navigator.mediaDevices?.getUserMedia) {
      fallback();
      return;
    }

    let stream = null;

    try {
      stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' }
      });
    } catch (error) {
      console.warn('Sankalp camera unavailable, using the file input:', error);
      fallback();
      return;
    }

    const video = document.createElement('video');
    video.autoplay = true;
    video.playsInline = true;
    video.srcObject = stream;

    const wrap = document.createElement('div');
    wrap.className = 'camera-capture';

    const shoot = document.createElement('button');
    shoot.type = 'button';
    shoot.className = 'camera-shoot';
    shoot.textContent = 'Capture';

    const cancel = document.createElement('button');
    cancel.type = 'button';
    cancel.className = 'camera-cancel';
    cancel.textContent = 'Cancel';

    function stop() {
      stream?.getTracks?.().forEach(track => track.stop());
      wrap.remove();
    }

    shoot.addEventListener('click', () => {
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth || 720;
      canvas.height = video.videoHeight || 960;
      canvas.getContext('2d').drawImage(video, 0, 0);

      canvas.toBlob(blob => {
        if (blob) {
          window.IndooneSend?.attach?.(
            new File([blob], `photo-${Date.now()}.jpg`, {
              type: 'image/jpeg'
            })
          );
        }

        stop();
      }, 'image/jpeg', 0.9);
    });

    cancel.addEventListener('click', stop);
    wrap.appendChild(video);
    wrap.appendChild(shoot);
    wrap.appendChild(cancel);
    document.body.appendChild(wrap);
  }

  window.IndoonePlusCamera = { pick };
})();
