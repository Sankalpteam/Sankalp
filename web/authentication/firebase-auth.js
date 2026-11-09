(() => {
  const getFirebase = () => window.IndooneFirebase;
  const cleanEmail = value => String(value || '').trim().toLowerCase();
  const normalizeMobile = value => String(value || '').replace(/[^0-9+]/g, '').replace(/^00/, '+');

  function errorMessage(error) {
    const map = {
      'auth/invalid-email': 'Enter a valid email address.',
      'auth/user-not-found': 'No Sankalp account was found.',
      'auth/wrong-password': 'Email or password is incorrect.',
      'auth/invalid-credential': 'Email or password is incorrect.',
      'auth/too-many-requests': 'Too many attempts. Please try again later.',
      'auth/network-request-failed': 'Network error. Check your connection and try again.',
      'auth/email-already-in-use': 'An account already exists with this email.',
      'auth/weak-password': 'Password should be at least 6 characters.'
    };
    return map[error?.code] || error?.message || 'Authentication failed.';
  }

  function db() {
    const fb = getFirebase();
    if (!fb?.database) throw new Error('Firebase Realtime Database is not initialized.');
    return fb.database;
  }

  async function emailForMobile(mobile) {
    const snapshot = await db().ref(`mobileIndex/${encodeURIComponent(mobile)}`).once('value');
    const value = snapshot.val();
    if (!value?.email) throw new Error('No Sankalp account is linked to this mobile number.');
    return value.email;
  }

  async function syncProfile(user, profile = {}) {
    if (!user) return;
    const now = Date.now();
    const payload = {
      uid: user.uid,
      email: user.email || profile.email || '',
      mobile: profile.mobile || '',
      updatedAt: now
    };
    await db().ref(`users/${user.uid}/profile`).update(payload);
    if (payload.mobile) {
      await db().ref(`mobileIndex/${encodeURIComponent(normalizeMobile(payload.mobile))}`).set({
        uid: user.uid,
        email: payload.email,
        updatedAt: now
      });
    }
  }

  // This module only exposes shared Firebase helpers. The OTP-aware auth
  // implementation in firebase-auth-otp.js owns window.IndooneFirebaseAuth.
  window.IndooneFirebaseAuthBase = {
    getFirebase,
    cleanEmail,
    normalizeMobile,
    errorMessage,
    db,
    emailForMobile,
    syncProfile,
  };
})();
