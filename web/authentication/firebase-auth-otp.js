// The web counterpart of android/authentication/IndooneAuthService.kt.
//
// Same endpoints, same payloads, same order of work:
//   login by OTP      request-otp -> verify-otp -> sign in with the custom token
//   sign up by OTP    request-otp -> verify-otp -> create the account
//
// There is no password path. Signing in is an identifier and a six-digit code, and a new
// account is created with a secret nobody is told - see randomSecret below.
//
// The one thing the web did not do before is sign in with the custom token the
// OTP service returns. Without it the OTP path could only ever work for someone
// who had already typed their details, which is not how the app works.
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

    return map[error?.code] || window.sankalpError?.fromError(error) || 'Authentication failed.';
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

  // 32 bytes of randomness as hex. crypto.getRandomValues is present in every browser this
  // page supports; the fallback exists so an old WebView cannot stop a signup.
  function randomSecret() {
    const bytes = new Uint8Array(32);

    if (window.crypto?.getRandomValues) {
      window.crypto.getRandomValues(bytes);
    } else {
      for (let index = 0; index < bytes.length; index += 1) {
        bytes[index] = Math.floor(Math.random() * 256);
      }
    }

    return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
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
      // Send the code. Do not ask Firebase first.
      //
      // This used to call fetchSignInMethodsForEmail and refuse to send anything unless it
      // listed a provider - so on any account where that call returns an empty list, which is
      // every account once Email Enumeration Protection is on (it is on by default for new
      // projects), the request was never made and no code ever arrived. The screen said "No
      // Sankalp account was found" for an account that exists.
      //
      // The app never did this: IndooneAuthService.resolveLoginEmail lowercases the address and
      // posts the request. One product, two behaviours, and the web's stopped the send.
      //
      // It is also the safer shape: telling anyone who asks whether an email has an account is
      // an enumeration leak. Whether the account exists is decided when the code is verified.
      return cleanEmail(raw);
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

  async function startSignup({ email, mobile }) {
    const cleanEmailValue = cleanEmail(email);
    const cleanMobile = normalizeMobile(mobile);
    if (!cleanEmailValue.includes('@')) {
      throw new Error('Enter a valid email address.');
    }

    if (!/^\+91\d{10}$/.test(cleanMobile)) {
      throw new Error('Enter a valid 10-digit Indian mobile number.');
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

      // The account is created with a secret nobody is ever told.
      //
      // Firebase's email/password provider needs *a* password to create the user, and the
      // account is not signed in with it afterwards: every login goes through the OTP service
      // and its custom token. So the password is random, generated here, and never stored,
      // shown or asked for. Nothing to remember, nothing to reset, and no way for anyone to
      // guess it into the account.
      const credential = await auth().createUserWithEmailAndPassword(
        pending.email,
        randomSecret()
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
        console.warn('Sankalp welcome email failed:', error);
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

  window.IndooneFirebaseAuth = {
    startLoginOtp,
    verifyLoginOtp,
    resendLoginOtp,
    startSignup,
    resendSignupOtp,
    finishSignupAfterOtp,
    syncProfile,
    errorMessage
  };
})();
