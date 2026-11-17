window.initMenuDeleteAccount = function () {
  const auth = window.IndooneFirebase?.auth;
  const db = window.IndooneFirebase?.database;
  const user = auth?.currentUser;

  if (!user) {
    toast('Please login first.');
    return;
  }

  const modal = document.getElementById('modal');

  modal.innerHTML = `
    <div class="modal-head">
      <h2>Delete Sankalp account?</h2>
      <button class="close-btn" data-close>×</button>
    </div>
    <p>
      This permanently deletes your Sankalp account, your cloud data and
      everything stored on this device. This action cannot be undone.
    </p>
    <div class="field">
      <label>ACCOUNT PASSWORD</label>
      <input
        id="deleteAccountPassword"
        type="password"
        autocomplete="current-password"
        placeholder="Enter your password"
      >
    </div>
    <div class="field">
      <label>TYPE DELETE TO CONFIRM</label>
      <input
        id="deleteAccountConfirm"
        type="text"
        autocomplete="off"
        placeholder="DELETE"
        autocapitalize="characters"
        spellcheck="false"
      >
    </div>
    <div id="accountDeleteStatus" class="auth-status" hidden></div>
    <button type="button" class="primary danger" id="confirmAccountDelete">
      Delete Sankalp account
    </button>
    <button type="button" class="secondary" data-close>
      Cancel
    </button>
  `;

  const setStatus = (message, isError = false) => {
    const status = document.getElementById('accountDeleteStatus');
    if (!status) return;

    status.hidden = false;
    status.textContent = message;

    if (isError) {
      status.setAttribute('data-error', 'true');
    } else {
      status.removeAttribute('data-error');
    }
  };

  const clearLocalData = () => {
    try {
      Object.keys(localStorage).forEach(key => {
        if (key.startsWith('indoone')) {
          localStorage.removeItem(key);
        }
      });
    } catch (_) {}

    try {
      Object.keys(sessionStorage).forEach(key => {
        if (key.startsWith('indoone')) {
          sessionStorage.removeItem(key);
        }
      });
    } catch (_) {}

    try {
      window.IndoonePersistence?.lock?.();
    } catch (_) {}

    try {
      window.IndoonePersistence?.clear?.();
    } catch (_) {}

  };

  document
    .getElementById('confirmAccountDelete')
    ?.addEventListener('click', async () => {
      const passwordInput = document.getElementById('deleteAccountPassword');
      const confirmInput = document.getElementById('deleteAccountConfirm');
      const button = document.getElementById('confirmAccountDelete');

      const password = String(passwordInput?.value || '');
      const confirmation = String(confirmInput?.value || '')
        .trim()
        .toUpperCase();

      if (!password) {
        passwordInput?.focus();
        toast('Enter your account password');
        return;
      }

      if (confirmation !== 'DELETE') {
        confirmInput?.focus();
        toast('Type DELETE to confirm');
        return;
      }

      if (!auth || !db || !auth.currentUser) {
        toast('Login session expired. Please login again.');
        return;
      }

      if (!auth.currentUser.email) {
        toast('This account cannot be re-authenticated here.');
        return;
      }

      if (button) {
        button.disabled = true;
      }

      setStatus('Verifying your account…');

      try {
        const provider = window.firebase?.auth?.EmailAuthProvider;
        const credential = provider?.credential?.(
          auth.currentUser.email,
          password
        );

        if (!credential) {
          throw new Error('Firebase email authentication is unavailable.');
        }

        await auth.currentUser.reauthenticateWithCredential(credential);
        await auth.currentUser.reload();

        const liveUser = auth.currentUser;
        if (!liveUser || liveUser.uid !== user.uid) {
          throw new Error('Login session expired. Please login again.');
        }

        setStatus('Removing your Sankalp cloud data…');

        // Only the paths this account is allowed to write.
        //
        // This used to gather every user node that carried the same email - a query over
        // `users`, ordered by profile/email - and put all of them into one atomic update,
        // together with their mobile-index entries. Firebase refuses a write to anybody
        // else's node, and a single refused path fails the WHOLE update. So the delete never
        // ran: the request was denied before it began, and the screen said "Firebase denied
        // deletion of your cloud data".
        //
        // The app was fixed for exactly this, and says so in DangerZoneScreen.kt. The web
        // kept the old shape.
        const profileSnapshot = await db
          .ref(`users/${liveUser.uid}/profile`)
          .once('value');
        const profile = profileSnapshot.val() || {};
        const email = String(liveUser.email || profile.email || '')
          .trim()
          .toLowerCase();
        const mobile = String(profile.mobile || '').trim();

        const updates = { [`users/${liveUser.uid}`]: null };

        if (mobile) {
          updates[`mobileIndex/${encodeURIComponent(mobile)}`] = null;
        }

        await db.ref().update(updates);

        // Best effort, and deliberately not awaited: any other node carrying this email is
        // cleanup, and it must never be able to block the deletion itself. The app does the
        // same, for the same reason.
        if (email) {
          try {
            db.ref('users')
              .orderByChild('profile/email')
              .equalTo(email)
              .once('value')
              .then(snapshot => {
                const duplicates = {};
                snapshot.forEach(child => {
                  if (child.key && child.key !== liveUser.uid) {
                    duplicates[`users/${child.key}`] = null;
                  }
                });
                if (Object.keys(duplicates).length) {
                  return db.ref().update(duplicates);
                }
                return null;
              })
              .catch(() => {});
          } catch (error) {
            // An index that is not configured must not stop the deletion.
          }
        }

        setStatus('Deleting your Sankalp account…');

        await liveUser.delete();

        clearLocalData();
        closeModal();
        window.location.reload();
      } catch (error) {
        if (button) {
          button.disabled = false;
        }

        const code = String(error?.code || '');

        if (
          code === 'auth/wrong-password' ||
          code === 'auth/invalid-credential'
        ) {
          setStatus('Incorrect account password.', true);
          toast('Incorrect account password');
          return;
        }

        if (code === 'auth/requires-recent-login') {
          setStatus(
            'A fresh login is required. Please login again and retry.',
            true
          );
          toast('Please login again, then retry account deletion');
          return;
        }

        if (
          code === 'PERMISSION_DENIED' ||
          /permission_denied/i.test(String(error?.message || ''))
        ) {
          setStatus(
            'Firebase denied deletion of your cloud data. Check Firebase database rules.',
            true
          );
          toast('Firebase denied cloud-data deletion');
          return;
        }

        if (code === 'auth/network-request-failed') {
          setStatus('Network error. Check your connection and try again.', true);
          toast('Network error. Please retry');
          return;
        }

        setStatus(error?.message || 'Account deletion failed.', true);
        toast(error?.message || 'Could not delete Sankalp account');
      }
    });
};
