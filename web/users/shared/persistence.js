window.IndoonePersistence = (() => {
  const PIN_KEY = 'indoone.app.lock.pin.v1';
  let unlocked = false;

  const encoder = new TextEncoder();

  function bytesToHex(bytes) {
    return Array.from(bytes)
      .map(byte => byte.toString(16).padStart(2, '0'))
      .join('');
  }

  async function hashPin(pin, salt) {
    const data = encoder.encode(
      `${salt}:${String(pin)}`
    );
    const digest = await crypto.subtle.digest(
      'SHA-256',
      data
    );

    return bytesToHex(new Uint8Array(digest));
  }

  async function save(_accounts, pin) {
    if (!/^\d{4,12}$/.test(String(pin))) {
      throw new Error('PIN must be 4–12 digits');
    }

    const saltBytes = crypto.getRandomValues(
      new Uint8Array(16)
    );
    const salt = bytesToHex(saltBytes);
    const hash = await hashPin(pin, salt);

    localStorage.setItem(
      PIN_KEY,
      JSON.stringify({
        v: 1,
        salt,
        hash
      })
    );

    unlocked = true;
  }

  async function unlock(pin) {
    const raw = localStorage.getItem(PIN_KEY);

    if (!raw) {
      return false;
    }

    let record;
    try {
      record = JSON.parse(raw);
    } catch (_) {
      return false;
    }

    if (!record?.salt || !record?.hash) {
      return false;
    }

    const hash = await hashPin(pin, record.salt);

    if (hash !== record.hash) {
      return false;
    }

    IndooneSecureSession.unlock(pin);
    unlocked = true;

    return true;
  }

  function isUnlocked() {
    return unlocked && !IndooneSecureSession.isLocked();
  }

  function lock() {
    unlocked = false;
    IndooneSecureSession.lock();
    window.indooneState.accounts = [];

    if (typeof renderAccounts === 'function') {
      renderAccounts();
    }
  }

  async function persistCurrent() {
    if (!isUnlocked()) {
      throw new Error('App is locked');
    }
  }

  return {
    save,
    unlock,
    lock,
    isUnlocked,
    persistCurrent,
    clear: () => {
      localStorage.removeItem(PIN_KEY);
      unlocked = false;
      IndooneSecureSession.lock();
    },
    storageKey: PIN_KEY
  };
})();
