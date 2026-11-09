(() => {
  const MARKUP_URL =
    'lobby/accounts/add-account/scan-qr/account-details/index.html?v=20261018j';
  const STYLE_URL =
    'lobby/accounts/add-account/scan-qr/account-details/style.css?v=20261018j';
  const FALLBACK_SCRIPT_URL =
    'https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.min.js';

  let stream = null;
  let detector = null;
  let video = null;
  let canvas = null;
  let context = null;
  let animationFrame = 0;
  let permissionWaiter = null;
  let active = false;
  let fallbackLoading = null;
  let scanBusy = false;

  function loadStyle() {
    const baseUrl = STYLE_URL.split('?')[0];

    if (
      document.querySelector(
        `link[href^="${baseUrl}"]`
      )
    ) {
      return;
    }

    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = STYLE_URL;
    document.head.appendChild(link);
  }

  async function loadMarkup() {
    const response = await fetch(MARKUP_URL, {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('QR page could not be loaded.');
    }

    return response.text();
  }

  function setStatus(message) {
    const status = document.getElementById(
      'addAccountQrStatus'
    );

    if (status) {
      status.textContent = message;
    }
  }

  function requestCameraPermission() {
    if (!window.IndooneNative?.requestCameraPermission) {
      return Promise.resolve(true);
    }

    if (permissionWaiter) {
      return permissionWaiter.promise;
    }

    let resolvePermission;
    const promise = new Promise(resolve => {
      resolvePermission = resolve;
    });

    permissionWaiter = {
      promise,
      resolve: resolvePermission
    };

    try {
      window.IndooneNative.requestCameraPermission();
    } catch (_) {
      permissionWaiter = null;
      return Promise.resolve(true);
    }

    window.setTimeout(() => {
      if (
        !permissionWaiter ||
        permissionWaiter.promise !== promise
      ) {
        return;
      }

      permissionWaiter = null;
      resolvePermission(true);
    }, 3000);

    return promise;
  }

  function handleCameraPermission(event) {
    const waiter = permissionWaiter;
    permissionWaiter = null;

    if (waiter) {
      waiter.resolve(
        Boolean(event.detail?.success)
      );
    }
  }

  function loadFallbackDecoder() {
    if (typeof window.jsQR === 'function') {
      return Promise.resolve(window.jsQR);
    }

    if (fallbackLoading) {
      return fallbackLoading;
    }

    fallbackLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = FALLBACK_SCRIPT_URL;
      script.async = true;

      script.onload = () => {
        if (typeof window.jsQR === 'function') {
          resolve(window.jsQR);
          return;
        }

        reject(
          new Error('QR decoder could not be initialized.')
        );
      };

      script.onerror = () => {
        reject(
          new Error('QR decoder could not be loaded.')
        );
      };

      document.head.appendChild(script);
    });

    return fallbackLoading;
  }

  function prepareFallbackCanvas() {
    canvas = document.createElement('canvas');
    context = canvas.getContext('2d', {
      willReadFrequently: true
    });
  }

  function readFallbackCode() {
    if (
      !video ||
      !context ||
      typeof window.jsQR !== 'function'
    ) {
      return '';
    }

    const width = video.videoWidth || 0;
    const height = video.videoHeight || 0;

    if (!width || !height) {
      return '';
    }

    const scale = Math.min(
      1,
      800 / width,
      600 / height
    );
    const targetWidth = Math.max(
      1,
      Math.floor(width * scale)
    );
    const targetHeight = Math.max(
      1,
      Math.floor(height * scale)
    );

    if (
      canvas.width !== targetWidth ||
      canvas.height !== targetHeight
    ) {
      canvas.width = targetWidth;
      canvas.height = targetHeight;
    }

    context.drawImage(
      video,
      0,
      0,
      targetWidth,
      targetHeight
    );

    const image = context.getImageData(
      0,
      0,
      targetWidth,
      targetHeight
    );

    const result = window.jsQR(
      image.data,
      image.width,
      image.height,
      {
        inversionAttempts: 'attemptBoth'
      }
    );

    return result?.data?.trim() || '';
  }

  function handleDecodedValue(rawValue) {
    const value = String(rawValue || '').trim();

    if (
      !value
        .toLowerCase()
        .startsWith('otpauth://')
    ) {
      setStatus(
        'QR detected. Please use a TOTP QR code.'
      );
      return false;
    }

    try {
      const parsed = TOTP.parseOtpAuth(value);

      if (!parsed.secret) {
        throw new Error('Missing TOTP secret.');
      }

      const zoho = window.IndooneZoho?.detect(
        parsed.issuer,
        parsed.label
      ) || null;

      stop();
      window.IndooneAddAccount?.showManual?.({
        push: true,
        prefill: {
          name:
            zoho?.service ||
            parsed.issuer ||
            parsed.label ||
            'Account',
          email: parsed.label || '',
          secret: parsed.secret,
          algorithm: parsed.algorithm,
          digits: parsed.digits,
          period: parsed.period,
          provider: zoho?.provider || '',
          service: zoho?.service || ''
        }
      });

      return true;
    } catch (error) {
      setStatus(
        error?.message ||
        'Unable to read this QR code.'
      );
      return false;
    }
  }

  async function scanLoop() {
    if (!active || !video) {
      return;
    }

    if (scanBusy) {
      animationFrame = requestAnimationFrame(
        scanLoop
      );
      return;
    }

    scanBusy = true;

    try {
      if (video.readyState >= 2) {
        let rawValue = '';

        if (detector) {
          const codes = await detector.detect(video);
          rawValue =
            codes?.[0]?.rawValue?.trim() || '';
        } else {
          rawValue = readFallbackCode();
        }

        if (rawValue) {
          handleDecodedValue(rawValue);
        }
      }
    } catch (error) {
      setStatus(
        error?.message ||
        'Unable to read this QR code.'
      );
    } finally {
      scanBusy = false;
    }

    if (active) {
      animationFrame = requestAnimationFrame(
        scanLoop
      );
    }
  }

  async function openCamera() {
    setStatus('Opening camera…');

    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error(
        'Camera is not available on this device.'
      );
    }

    video = document.getElementById(
      'addAccountQrVideo'
    );

    if (!video) {
      throw new Error(
        'QR camera view is unavailable.'
      );
    }

    const constraints = {
      video: {
        facingMode: {
          ideal: 'environment'
        },
        width: {
          ideal: 1280
        },
        height: {
          ideal: 720
        }
      },
      audio: false
    };

    let lastError = null;

    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        stream = await navigator.mediaDevices.getUserMedia(
          constraints
        );
        break;
      } catch (error) {
        lastError = error;

        if (attempt < 2) {
          await new Promise(resolve =>
            window.setTimeout(resolve, 500)
          );
        }
      }
    }

    if (!stream) {
      if (lastError?.name === 'NotAllowedError') {
        throw new Error(
          'Camera permission was denied.'
        );
      }

      throw new Error('Unable to open the camera.');
    }

    video.srcObject = stream;
    await video.play();

    active = true;
    setStatus(
      'Scanning for a TOTP QR code…'
    );

    if ('BarcodeDetector' in window) {
      try {
        detector = new BarcodeDetector({
          formats: ['qr_code']
        });
      } catch (_) {
        detector = null;
      }
    }

    if (!detector) {
      prepareFallbackCanvas();
      setStatus(
        'Scanning for a TOTP QR code…'
      );

      try {
        await loadFallbackDecoder();
      } catch (_) {
        setStatus(
          'Camera is ready, but QR decoding is unavailable in this browser.'
        );
      }
    }

    animationFrame = requestAnimationFrame(
      scanLoop
    );
  }

  async function start() {
    stop();
    setStatus('Checking camera…');

    const permissionGranted =
      await requestCameraPermission();

    if (!permissionGranted) {
      setStatus(
        'Camera permission is required to scan a QR code.'
      );
      return;
    }

    try {
      await openCamera();
    } catch (error) {
      stop();
      setStatus(
        error?.message ||
        'Unable to open the camera.'
      );
    }
  }

  function stop() {
    active = false;
    scanBusy = false;

    if (animationFrame) {
      cancelAnimationFrame(animationFrame);
    }

    animationFrame = 0;

    if (stream) {
      stream
        .getTracks()
        .forEach(track => track.stop());
    }

    stream = null;

    if (video) {
      video.srcObject = null;
    }

    video = null;
    detector = null;
    canvas = null;
    context = null;
  }

  function backToMenu() {
    stop();
    window.IndooneAddAccount?.showMenu?.({
      push: true
    });
  }

  function bind(root) {
    root
      .querySelectorAll('[data-qr-action]')
      .forEach(button => {
        button.addEventListener('click', event => {
          event.preventDefault();

          const action = button.dataset.qrAction;

          if (
            action === 'back' ||
            action === 'cancel'
          ) {
            backToMenu();
          }
        });
      });
  }

  async function render(mount) {
    loadStyle();
    stop();

    try {
      mount.innerHTML = await loadMarkup();
      bind(mount);
      await start();
    } catch (error) {
      mount.innerHTML = `
        <section class="add-account-child qr-page">
          <button
            type="button"
            class="page-back"
            data-qr-action="back"
          >
            <span aria-hidden="true">‹</span>
            <span>Back</span>
          </button>
          <h1>Scan QR Code</h1>
          <p>
            ${error?.message || 'Unable to load the QR scanner.'}
          </p>
        </section>
      `;
      bind(mount);
      console.error(
        'Sankalp Add Account QR feature failed:',
        error
      );
    }
  }

  document.addEventListener(
    'indoone-camera-permission',
    handleCameraPermission
  );

  window.IndooneAddAccountQr = {
    render,
    start,
    stop
  };
})();
