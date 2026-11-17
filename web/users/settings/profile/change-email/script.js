// The web counterpart of android/settings/profile/change-email/ChangeEmailScreen.kt.
//
// A menu feature, opened by menu/script.js the same way Profile and Danger Zone
// are: the loader fetches the fragment into #modal, calls this initializer, then
// shows the page. The app draws three things on this one page - the address
// already on the account, read-only; the new address; and the current password -
// and so does this.
//
// The old address used to be typed into the same field that held the new one,
// which read as though it were up for editing. It is a read-only box now, the
// way the app draws CurrentValueField.
window.initMenuChangeEmail = function () {
  const page = document.getElementById('modal');

  if (!page) {
    return false;
  }

  const auth = window.IndooneFirebase?.auth;
  const db = window.IndooneFirebase?.database;
  const user = auth?.currentUser;

  const currentValue = document.getElementById('currentEmailValue');
  const input = document.getElementById('newEmail');
  const passwordInput = document.getElementById('currentEmailPassword');
  const saveButton = document.getElementById('saveEmail');
  const status = document.getElementById('changeEmailStatus');

  page
    .querySelector('[data-change-email-action="back"]')
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
    setStatus('Please sign in again before changing your email.', 'error');
    return true;
  }

  if (currentValue) {
    currentValue.textContent = user.email || 'Not set';
  }

  saveButton?.addEventListener('click', async () => {
    const value = input?.value.trim().toLowerCase() || '';
    const password = passwordInput?.value || '';

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
      setStatus('Enter a valid email address.', 'error');
      return;
    }

    if (!password) {
      setStatus('Enter your current password', 'error');
      return;
    }

    if (value === String(user.email || '').trim().toLowerCase()) {
      setStatus('Enter a different email address.', 'error');
      return;
    }

    saveButton.disabled = true;
    saveButton.textContent = 'Changing…';
    setStatus('');

    try {
      const methods =
        await auth.fetchSignInMethodsForEmail(value).catch(error => {
          if (error?.code === 'auth/invalid-email') {
            throw error;
          }

          return [];
        });

      if (Array.isArray(methods) && methods.length) {
        throw new Error('An account already exists with this email.');
      }

      const credential = firebase.auth.EmailAuthProvider.credential(
        user.email,
        password
      );

      await user.reauthenticateWithCredential(credential);
      await user.updateEmail(value);

      const snapshot = await db
        .ref(`users/${user.uid}/profile`)
        .once('value');
      const profile = snapshot.val() || {};

      const updates = {
        [`users/${user.uid}/profile/email`]: value,
        [`users/${user.uid}/profile/uid`]: user.uid,
        [`users/${user.uid}/profile/updatedAt`]: Date.now()
      };

      if (profile.mobile) {
        const mobile =
          window.IndooneFirebaseAuthBase?.normalizeMobile?.(
            profile.mobile
          ) || profile.mobile;

        updates[`users/${user.uid}/profile/mobile`] = mobile;

        updates[`mobileIndex/${encodeURIComponent(mobile)}`] = {
          uid: user.uid,
          email: value,
          updatedAt: Date.now()
        };
      }

      await db.ref().update(updates);

      await user.sendEmailVerification().catch(error =>
        console.warn('Email verification send failed:', error)
      );

      if (currentValue) {
        currentValue.textContent = value;
      }

      if (input) {
        input.value = '';
      }

      if (passwordInput) {
        passwordInput.value = '';
      }

      setStatus(
        'Email changed. A verification email was sent.'
      );
    } catch (error) {
      const messages = {
        'auth/wrong-password': 'Current password is incorrect.',
        'auth/invalid-credential': 'Current password is incorrect.',
        'auth/requires-recent-login':
          'Please sign in again, then try this change.',
        'auth/email-already-in-use':
          'An account already exists with this email.',
        'auth/invalid-email': 'Enter a valid email address.'
      };

      setStatus(
        messages[error?.code] ||
          error?.message ||
          'Could not change email.',
        'error'
      );
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = 'Change email';
    }
  });

  return true;
};
