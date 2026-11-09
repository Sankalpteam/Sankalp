// The web counterpart of android/authentication/IndooneAuthService.kt.
//
// Same endpoints, same payloads, same order of work:
//   login by OTP      request-otp -> verify-otp -> sign in with the custom token
//   login by password sign in with the password, then check the profile
//   sign up by OTP    request-otp -> verify-otp -> create the account
//   sign up by password create the account straight away
//
// The one thing the web did not do before is sign in with the custom token the
// OTP service returns. Without it the OTP path could only ever work for someone
// who had already typed their password, which is not how the app works.
(() => {
  const APP_NAME = 'Sankalp user';
  const LOGIN_PENDING_KEY = 'indoone_login_pending';
  const SIGNUP_PENDING_KEY = 'indoone_signup_pending';

  const getFirebase = () => window.IndooneFirebase;
  const getVerification = () => window.IndooneIndoVerification;

  const cleanEmail = value => String(value || '').trim().toLowerCase();

  function normalizeMobile(value) {
    const raw = String(value || '').trim();
    const digits = raw.replace(/\D/g, '');

    if (/^91\d{10}$/.test(digits)) {
      return `+${digits}`;
    }

    if (/^\d{10}$/.test(digits)) {
      return `+91${digits}`;
    }

    return raw.replace(/[^0-9+]/g, '').replace(/^00/, '+');
  }

  function mobileCandidates(value) {
    const normalized = normalizeMobile(value);
    const digits = normalized.replace(/\D/g, '');
    const ten = digits.length >= 10 ? digits.slice(-10) : digits;

    return Array.from(new Set([
      normalized,
      `+91${ten}`,
      ten,
      `91${ten}`
    ].filter(Boolean)));
  }

  function errorMessage(error) {
    // The app's own strings, from IndooneAuthService.normalizeError. The app has
    // no user-disabled case, so that entry is gone; PERMISSION_DENIED is the
    // app's Firebase-database message.
    const map = {
      'auth/invalid-email': 'Enter a valid email address.',
      'auth/user-not-found': 'No Sankalp account was found.',
      'auth/wrong-password': 'Email or password is incorrect.',
      'auth/invalid-credential': 'Email or password is incorrect.',
      'auth/email-already-in-use': 'An account already exists with this email.',
      'auth/weak-password': 'Password should be at least 6 characters.',
      'auth/too-many-requests': 'Too many attempts. Please try again later.',
      'auth/network-request-failed': 'Network error. Check your connection and try again.',
      'PERMISSION_DENIED': 'Sankalp account data is temporarily unavailable. Please try again or use your email address.'
    };

    return map[error?.code] || error?.message || 'Authentication failed.';
  }

  function db() {
    return getFirebase()?.database;
  }

  function auth() {
    return getFirebase()?.auth;
  }

  function verification() {
    const api = getVerification();

    if (!api) {
      throw new Error('OTP service is still loading. Please try again.');
    }

    return api;
  }

  function setAuthSession(uid) {
    localStorage.setItem('indoone_otp_verified_uid', uid);
    localStorage.setItem('indoone_authenticated_uid', uid);
    sessionStorage.setItem('indoone_otp_verified_uid', uid);
    sessionStorage.setItem('indoone_authenticated_uid', uid);
  }

  function clearAuthSession() {
    localStorage.removeItem('indoone_otp_verified_uid');
    localStorage.removeItem('indoone_authenticated_uid');
    sessionStorage.removeItem('indoone_otp_verified_uid');
    sessionStorage.removeItem('indoone_authenticated_uid');
  }

  async function mobileIdentity(mobile) {
    for (const candidate of mobileCandidates(mobile)) {
      const key = encodeURIComponent(candidate);
      const snapshot = await db().ref(`mobileIndex/${key}`).once('value');
      const value = snapshot.val();

      if (value) {
        return {
          uid: value.uid || value.userId || '',
          email: cleanEmail(value.email || '')
        };
      }
    }

    throw new Error('No Sankalp account was found for this mobile number.');
  }

  async function identityExists(email, mobile) {
    const methods = await auth().fetchSignInMethodsForEmail(email);

    if (methods?.length) {
      return true;
    }

    for (const candidate of mobileCandidates(mobile)) {
      const key = encodeURIComponent(candidate);
      const snapshot = await db().ref(`mobileIndex/${key}`).once('value');

      if (snapshot.exists()) {
        return true;
      }
    }

    return false;
  }

  // The app's resolveLoginEmail: an email is checked for an existing account,
  // a mobile number is looked up in the index.
  async function resolveLoginEmail(raw) {
    if (raw.includes('@')) {
      const email = cleanEmail(raw);
      const methods = await auth().fetchSignInMethodsForEmail(email);

      if (!methods?.length) {
        throw new Error('No Sankalp account was found.');
      }

      return email;
    }

    const identity = await mobileIdentity(raw);

    if (!identity.email) {
      throw new Error('No Sankalp account was found for this mobile number.');
    }

    return identity.email;
  }

  // The app's validateLoginProfile.
  async function validateLoginProfile(uid, email, rawIdentifier) {
    const snapshot = await db().ref(`users/${uid}/profile`).once('value');
    const profile = snapshot.val() || {};
    const savedEmail = cleanEmail(profile.email || '');

    if (savedEmail && savedEmail !== email) {
      throw new Error('The account profile does not match this email address.');
    }

    if (!rawIdentifier.includes('@')) {
      const expected = normalizeMobile(rawIdentifier);
      const saved = normalizeMobile(profile.mobile || '');

      if (saved && saved !== expected) {
        throw new Error('This mobile number is not linked to this Sankalp account.');
      }
    }
  }

  // The app's syncProfile writes uid, email, updatedAt and mobile, and keeps the
  // mobileIndex entry in step (IndooneAuthService.syncProfile).
  async function syncProfile(user, profile = {}) {
    if (!user?.uid) {
      return;
    }

    const email = cleanEmail(profile.email || user.email || '');
    const mobile = normalizeMobile(profile.mobile || '');
    const now = Date.now();
    const payload = { uid: user.uid, email, updatedAt: now };

    if (mobile) {
      payload.mobile = mobile;
    }

    await db().ref(`users/${user.uid}/profile`).update(payload);

    if (mobile) {
      await db().ref(`mobileIndex/${encodeURIComponent(mobile)}`).set({
        uid: user.uid,
        email,
        updatedAt: now
      });
    }
  }

  function readPending(key) {
    try {
      const raw = sessionStorage.getItem(key);
      return raw ? JSON.parse(raw) : null;
    } catch (error) {
      return null;
    }
  }

  function writePending(key, value) {
    if (value) {
      sessionStorage.setItem(key, JSON.stringify(value));
    } else {
      sessionStorage.removeItem(key);
    }
  }

  // --------------------------------------------------------------- login

  // Step one. No password is asked for, which is the point of this path.
  async function startLoginOtp(identifier) {
    const raw = String(identifier || '').trim();

    if (!raw) {
      throw new Error('Enter your email/mobile number.');
    }

    const email = await resolveLoginEmail(raw);
    const result = await verification().requestLoginOtp(email, APP_NAME);

    if (!result?.challengeId) {
      throw new Error('OTP service did not return a challenge ID.');
    }

    writePending(LOGIN_PENDING_KEY, {
      email,
      rawIdentifier: raw,
      challengeId: result.challengeId,
      requestedAt: Date.now()
    });

    return email;
  }

  async function verifyLoginOtp(otp) {
    const pending = readPending(LOGIN_PENDING_KEY);

    if (!pending?.challengeId) {
      throw new Error('Please request a new OTP.');
    }

    const code = String(otp || '').replace(/\D/g, '');

    if (code.length !== 6) {
      throw new Error('Enter the 6-digit OTP.');
    }

    const result = await verification().verifyLoginOtp({
      email: pending.email,
      challengeId: pending.challengeId,
      otp: code,
      name: APP_NAME
    });

    if (!result?.verified) {
      throw new Error(result?.error || 'OTP verification failed.');
    }

    const customToken = [
      result.customToken,
      result.firebaseCustomToken,
      result.firebaseToken,
      result.token
    ].find(value => value);

    window.__indooneAuthPending = true;

    try {
      // The app accepts an existing session when the service returns no custom
      // token (IndooneAuthService.verifyLoginOtp falls back to auth.currentUser);
      // only when neither is present does it fail.
      const user = customToken
        ? (await auth().signInWithCustomToken(customToken))?.user
        : auth().currentUser;

      if (!user) {
        throw new Error(
          customToken
            ? 'OTP verified, but login session could not be created.'
            : 'OTP was verified, but the verification service did not return a login session.'
        );
      }

      await validateLoginProfile(user.uid, pending.email, pending.rawIdentifier);
      await syncProfile(user, { email: pending.email });
      setAuthSession(user.uid);
      writePending(LOGIN_PENDING_KEY, null);
      window.__indooneAuthPending = false;
      window.IndooneAuthUI?.close?.();

      if (typeof window.loadFirebaseAccounts === 'function') {
        await window.loadFirebaseAccounts();
      }

      if (typeof renderAccounts === 'function') {
        renderAccounts();
      }
    } catch (error) {
      await auth().signOut().catch(() => {});
      window.__indooneAuthPending = false;
      writePending(LOGIN_PENDING_KEY, null);
      clearAuthSession();
      throw error;
    }
  }

  async function resendLoginOtp() {
    const pending = readPending(LOGIN_PENDING_KEY);

    if (!pending?.email) {
      throw new Error('Login session expired. Please login again.');
    }

    const result = await verification().resendOtp(pending.email, 'login');

    if (!result?.challengeId) {
      throw new Error('OTP service did not return a new challenge ID.');
    }

    pending.challengeId = result.challengeId;
    pending.requestedAt = Date.now();
    writePending(LOGIN_PENDING_KEY, pending);

    return pending.email;
  }

  // Step three. Password, straight in, no OTP - the same as the app.
  async function loginWithPassword(identifier, password) {
    const raw = String(identifier || '').trim();
    const secret = String(password || '');

    if (!raw || !secret) {
      throw new Error('Enter your email/mobile number and password.');
    }

    const email = await resolveLoginEmail(raw);
    clearAuthSession();
    window.__indooneAuthPending = true;

    try {
      const credential = await auth().signInWithEmailAndPassword(email, secret);
      const user = credential?.user;

      if (!user) {
        throw new Error('Login session expired. Please login again.');
      }

      await validateLoginProfile(user.uid, email, raw);
      await syncProfile(user, { email });
      setAuthSession(user.uid);
      window.__indooneAuthPending = false;
      window.IndooneAuthUI?.close?.();

      if (typeof window.loadFirebaseAccounts === 'function') {
        await window.loadFirebaseAccounts();
      }

      if (typeof renderAccounts === 'function') {
        renderAccounts();
      }
    } catch (error) {
      await auth().signOut().catch(() => {});
      window.__indooneAuthPending = false;
      clearAuthSession();
      throw error;
    }
  }

  // -------------------------------------------------------------- signup

  async function startSignup({ email, mobile, password }) {
    const cleanEmailValue = cleanEmail(email);
    const cleanMobile = normalizeMobile(mobile);
    const secret = String(password || '');

    if (!cleanEmailValue.includes('@')) {
      throw new Error('Enter a valid email address.');
    }

    if (!/^\+91\d{10}$/.test(cleanMobile)) {
      throw new Error('Enter a valid 10-digit Indian mobile number.');
    }

    if (secret.length < 6) {
      throw new Error('Password should be at least 6 characters.');
    }

    if (await identityExists(cleanEmailValue, cleanMobile)) {
      throw new Error('An account already exists with this email or mobile number.');
    }

    const result = await verification().requestSignupOtp(cleanEmailValue, APP_NAME);

    if (!result?.challengeId) {
      throw new Error('OTP service did not return a challenge ID.');
    }

    writePending(SIGNUP_PENDING_KEY, {
      email: cleanEmailValue,
      mobile: cleanMobile,
      password: secret,
      challengeId: result.challengeId,
      requestedAt: Date.now()
    });

    return cleanEmailValue;
  }

  async function resendSignupOtp() {
    const pending = readPending(SIGNUP_PENDING_KEY);

    if (!pending?.email) {
      throw new Error('Signup session expired. Enter your details again.');
    }

    const result = await verification().resendOtp(pending.email, 'signup');

    if (!result?.challengeId) {
      throw new Error('OTP service did not return a new challenge ID.');
    }

    pending.challengeId = result.challengeId;
    pending.requestedAt = Date.now();
    writePending(SIGNUP_PENDING_KEY, pending);

    return pending.email;
  }

  async function finishSignupAfterOtp(otp) {
    const pending = readPending(SIGNUP_PENDING_KEY);

    if (!pending?.challengeId) {
      throw new Error('Signup session expired. Enter your details again.');
    }

    const code = String(otp || '').replace(/\D/g, '');

    if (code.length !== 6) {
      throw new Error('Enter the 6-digit OTP.');
    }

    const result = await verification().verifySignupOtp({
      email: pending.email,
      challengeId: pending.challengeId,
      otp: code,
      name: APP_NAME
    });

    if (!result?.verified) {
      throw new Error(result?.error || 'OTP verification failed.');
    }

    window.__indooneAuthPending = true;

    try {
      if (await identityExists(pending.email, pending.mobile)) {
        throw new Error('An account already exists with this email or mobile number.');
      }

      const credential = await auth().createUserWithEmailAndPassword(
        pending.email,
        pending.password
      );
      const user = credential?.user;

      if (!user) {
        throw new Error('Could not create the Sankalp account.');
      }

      await syncProfile(user, {
        email: pending.email,
        mobile: pending.mobile
      });

      try {
        await verification().sendSignupWelcome({
          email: pending.email,
          welcomeToken: result.welcomeToken,
          name: APP_NAME
        });
      } catch (error) {
        console.warn('Indoone welcome email failed:', error);
      }

      setAuthSession(user.uid);
      writePending(SIGNUP_PENDING_KEY, null);
      window.__indooneAuthPending = false;
      window.IndooneAuthUI?.close?.();

      if (typeof window.loadFirebaseAccounts === 'function') {
        await window.loadFirebaseAccounts();
      }

      if (typeof renderAccounts === 'function') {
        renderAccounts();
      }
    } catch (error) {
      await auth().signOut().catch(() => {});
      window.__indooneAuthPending = false;
      writePending(SIGNUP_PENDING_KEY, null);
      clearAuthSession();
      throw error;
    }
  }

  // Sign up with the password directly, no OTP - the app's other signup path.
  async function createSignupWithPassword({ email, mobile, password }) {
    const cleanEmailValue = cleanEmail(email);
    const cleanMobile = normalizeMobile(mobile);
    const secret = String(password || '');

    if (!cleanEmailValue.includes('@')) {
      throw new Error('Enter a valid email address.');
    }

    if (!/^\+91\d{10}$/.test(cleanMobile)) {
      throw new Error('Enter a valid 10-digit Indian mobile number.');
    }

    if (secret.length < 6) {
      throw new Error('Password should be at least 6 characters.');
    }

    window.__indooneAuthPending = true;

    try {
      if (await identityExists(cleanEmailValue, cleanMobile)) {
        throw new Error('An account already exists with this email or mobile number.');
      }

      const credential = await auth().createUserWithEmailAndPassword(
        cleanEmailValue,
        secret
      );
      const user = credential?.user;

      if (!user) {
        throw new Error('Could not create the Sankalp account.');
      }

      await syncProfile(user, {
        email: cleanEmailValue,
        mobile: cleanMobile
      });
      setAuthSession(user.uid);
      window.__indooneAuthPending = false;
      window.IndooneAuthUI?.close?.();

      if (typeof window.loadFirebaseAccounts === 'function') {
        await window.loadFirebaseAccounts();
      }

      if (typeof renderAccounts === 'function') {
        renderAccounts();
      }
    } catch (error) {
      await auth().signOut().catch(() => {});
      window.__indooneAuthPending = false;
      clearAuthSession();
      throw error;
    }
  }

  window.IndooneFirebaseAuth = {
    startLoginOtp,
    verifyLoginOtp,
    resendLoginOtp,
    loginWithPassword,
    startSignup,
    resendSignupOtp,
    finishSignupAfterOtp,
    createSignupWithPassword,
    syncProfile,
    errorMessage
  };
})();
