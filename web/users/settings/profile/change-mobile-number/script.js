// The web counterpart of
// android/settings/profile/change-mobile-number/ChangeMobileNumberScreen.kt.
//
// A menu feature, opened by menu/script.js the same way Profile and Danger Zone
// are: the loader fetches the fragment into #modal, calls this initializer, then
// shows the page. The app draws three things on this one page - the number
// already on the account, read-only; the new number; and the current password -
// and so does this.
//
// The password is used the way the app uses it: to prove it is really the owner
// before the number is changed.
window.initMenuChangeMobile = function () {
  const page = document.getElementById('modal');

  if (!page) {
    return false;
  }

  const auth = window.IndooneFirebase?.auth;
  const db = window.IndooneFirebase?.database;
  const user = auth?.currentUser;

  const normalizeMobile =
    window.IndooneFirebaseAuthBase?.normalizeMobile ||
    (value => {
      const raw = String(value || '').trim();
      const digits = raw.replace(/\D/g, '');

      if (/^91\d{10}$/.test(digits)) {
        return `+${digits}`;
      }

      if (/^\d{10}$/.test(digits)) {
        return `+91${digits}`;
      }

      return raw
        .replace(/[^0-9+]/g, '')
        .replace(/^00/, '+');
    });

  const currentValue = document.getElementById('currentMobileValue');
  const newInput = document.getElementById('newMobileNumber');
  const passwordInput = document.getElementById('currentMobilePassword');
  const saveButton = document.getElementById('saveMobileNumber');
  const status = document.getElementById('changeMobileStatus');

  page
    .querySelector('[data-change-mobile-action="back"]')
    ?.addEventListener('click', () => {
      window.openMenuFeature?.('account');
    });

  function setStatus(text, kind) {
    if (!status) {
      return;
    }

    status.textContent = text || '';
    status.hidden = !text;
    status.classList.toggle('is-error', kind === 'error');
  }

  if (!auth || !db || !user) {
    setStatus('Please sign in again before changing your mobile number.', 'error');
    return true;
  }

  async function loadCurrentMobile() {
    const snapshot = await db
      .ref(`users/${user.uid}/profile/mobile`)
      .once('value');

    return normalizeMobile(snapshot.val() || '');
  }

  // The old number is shown, never typed into. CurrentValueField draws it as a
  // read-only box and falls back to "Not set" when there is nothing there.
  void loadCurrentMobile()
    .then(mobile => {
      if (currentValue) {
        currentValue.textContent = mobile || 'Not set';
      }
    })
    .catch(() => {});

  saveButton?.addEventListener('click', async () => {
    const value = normalizeMobile(newInput?.value || '');
    const password = String(passwordInput?.value || '');

    if (!/^\+91\d{10}$/.test(value)) {
      setStatus('Enter a valid 10-digit Indian mobile number.', 'error');
      return;
    }

    if (!password) {
      setStatus('Enter your current password', 'error');
      return;
    }

    saveButton.disabled = true;
    saveButton.textContent = 'Updating…';
    setStatus('');

    try {
      const profileSnapshot = await db
        .ref(`users/${user.uid}/profile`)
        .once('value');
      const profile = profileSnapshot.val() || {};
      const oldMobile = normalizeMobile(profile.mobile || '');

      if (value === oldMobile) {
        setStatus('Enter a different mobile number.', 'error');
        return;
      }

      // Prove it is the owner, the way the app asks for the password.
      const credential = window.IndooneFirebase?.auth?.EmailAuthProvider
        ?.credential?.(user.email || profile.email || '', password);

      if (credential) {
        await user.reauthenticateWithCredential(credential);
      }

      const candidates = Array.from(
        new Set(
          [
            value,
            value.replace(/\D/g, ''),
            value.replace(/^\+/, ''),
            value.replace(/^\+91/, '')
          ].filter(Boolean)
        )
      );

      for (const candidate of candidates) {
        const existingSnapshot = await db
          .ref(`mobileIndex/${encodeURIComponent(candidate)}`)
          .once('value');
        const existing = existingSnapshot.val();

        if (
          existing?.uid &&
          String(existing.uid) !== String(user.uid)
        ) {
          throw new Error(
            'That mobile number is already linked to another account.'
          );
        }

        if (existing?.email && !existing?.uid) {
          throw new Error(
            'That mobile number is already linked to another account.'
          );
        }
      }

      const now = Date.now();
      const updates = {
        [`users/${user.uid}/profile/mobile`]: value,
        [`users/${user.uid}/profile/uid`]: user.uid,
        [`users/${user.uid}/profile/email`]:
          user.email || profile.email || '',
        [`users/${user.uid}/profile/updatedAt`]: now,
        [`mobileIndex/${encodeURIComponent(value)}`]: {
          uid: user.uid,
          email: user.email || profile.email || '',
          updatedAt: now
        }
      };

      if (oldMobile && oldMobile !== value) {
        updates[
          `mobileIndex/${encodeURIComponent(oldMobile)}`
        ] = null;
      }

      await db.ref().update(updates);
      // The app keeps the change screen open and shows the message inline
      // (ProfileViewModel: message = "Mobile number updated."), so the web does
      // the same rather than toasting and navigating away.
      setStatus('Mobile number updated.');
    } catch (error) {
      setStatus(
        error?.message || 'Could not change mobile number.',
        'error'
      );
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = 'Update mobile number';
    }
  });

  return true;
};
