// The authentication screen controller.
//
// It renders whichever screen the user is on and drives the app's steps. The
// work itself lives in firebase-auth-otp.js, which mirrors IndooneAuthService;
// this file only moves between steps and reports what happened.
//
// The screen is rendered ONCE. Moving between the app's steps only shows and
// hides panels. It used to re-render the whole screen on every step change,
// which wiped everything the user had typed - the identifier was empty by the
// time the password step appeared, so Complete Login could never work.
(() => {
  const AUTH_CLASS = 'indoone-auth-screen';
  let busy = false;
  let currentScreen = null;

  const $ = id => document.getElementById(id);

  // The auth screen has a container of its own - the card inside #overlay, which
  // nothing else uses. It must NOT be #modal: #modal is the element every menu
  // page and the Settings screens are written into, so putting the sign-in
  // backdrop on it painted every one of those pages beige.
  function host() {
    return $('accountDialog');
  }

  function root() {
    return host()?.querySelector('.auth-page') || document;
  }

  function setStatus(message = '', error = false) {
    const node = $('authStatus');

    if (!node) return;

    node.textContent = message;
    node.hidden = !message;
    node.dataset.error = error ? 'true' : 'false';
  }

  function showShell(html) {
    const overlay = $('overlay');
    const modal = $('modal');

    if (!overlay || !modal) return false;

    // The card goes into the overlay's own child, and the overlay is the
    // backdrop. The overlay belongs to the sign-in screen and to nothing else.
    const card = host();

    if (!card) return false;

    overlay.classList.remove('hidden');
    overlay.classList.add(AUTH_CLASS);
    document.body.classList.add('auth-open', 'auth-pending');

    // The status line sits below the card, not inside it, which is where the
    // app puts it.
    card.innerHTML = `
      <div class="auth-page">
        ${html}
      </div>
      <div
        id="authStatus"
        class="auth-status"
        hidden
        aria-live="polite"
      ></div>
    `;

    bindButtons(card);
    bindMobileDefaults(card);
    bindPasswordToggles(card);

    return true;
  }

  // Render the screen only if it is not already the one on display, so a step
  // change never throws away what has been typed.
  function renderScreen(name, html) {
    if (currentScreen === name && host()?.querySelector('.auth-page')) {
      return true;
    }

    if (!showShell(html)) {
      return false;
    }

    currentScreen = name;
    return true;
  }

  function mobileField(id, prefixId, placeholder = '98765 43210', login = false) {
    const inputMode = login ? 'text' : 'tel';
    const type = login ? 'text' : 'tel';
    const autocomplete = login ? 'username' : 'tel';
    const maxLength = login ? 320 : 13;
    const name = login ? 'login-identifier' : 'phone';

    return `
      <div class="mobile-field">
        <span
          id="${prefixId}"
          class="mobile-prefix"
          hidden
        >+91</span>
        <input
          id="${id}"
          name="${name}"
          type="${type}"
          inputmode="${inputMode}"
          autocomplete="${autocomplete}"
          autocapitalize="none"
          spellcheck="false"
          maxlength="${maxLength}"
          placeholder="${placeholder}"
        />
      </div>
    `;
  }

  function isEmailLike(value) {
    return /[A-Za-z@]/.test(String(value || ''));
  }

  function normalizedMobileDigits(value) {
    const digits = String(value || '').replace(/\D/g, '');

    if (/^91\d{10}$/.test(digits)) {
      return digits.slice(2);
    }

    return digits.slice(0, 10);
  }

  // Mobile input handling must be visual-only. Never rewrite value, selection,
  // focus, type, or inputmode while an IME/keyboard is composing text.
  function syncMobilePrefix(input, prefix) {
    if (!input || !prefix) return;

    const raw = String(input.value || '');

    if (isEmailLike(raw)) {
      prefix.hidden = true;
      input.classList.remove('has-mobile-prefix');
      return;
    }

    const showPrefix = normalizedMobileDigits(raw).length > 0;

    prefix.hidden = !showPrefix;
    input.classList.toggle('has-mobile-prefix', showPrefix);
  }

  function bindMobileInput(input, prefix) {
    if (!input || input.dataset.indiaMobileBound === 'true') return;

    input.dataset.indiaMobileBound = 'true';

    input.addEventListener('input', () => {
      // Only update presentation. The input's value remains browser/IME owned.
      syncMobilePrefix(input, prefix);
    });
  }

  function bindMobileDefaults(scope) {
    // The login identifier shows +91 while it looks like a number. The password
    // step's identifier has no prefix in the app, so it is not bound here - it
    // was, and it only pushed the text sideways with no prefix visible.
    bindMobileInput(
      scope.querySelector('#loginIdentifier'),
      scope.querySelector('#loginMobilePrefix')
    );

    bindMobileInput(
      scope.querySelector('#signupMobile'),
      scope.querySelector('#signupMobilePrefix')
    );
  }

  function bindPasswordToggles(scope) {
    scope
      .querySelectorAll('button[data-password-toggle]')
      .forEach(button => {
        if (button.dataset.passwordToggleBound === 'true') return;

        button.dataset.passwordToggleBound = 'true';

        button.addEventListener('click', event => {
          event.preventDefault();
          event.stopPropagation();

          const input = scope.querySelector(
            `#${button.dataset.passwordToggle}`
          );

          if (!input) return;

          const showing = input.type === 'text';
          input.type = showing ? 'password' : 'text';
          button.textContent = showing ? '◉' : '◌';
          button.setAttribute(
            'aria-label',
            showing ? 'Show password' : 'Hide password'
          );
          input.focus();
        });
      });
  }

  function passwordField(id, autocomplete, placeholder) {
    return `
      <div class="password-wrap">
        <input
          id="${id}"
          name="${id}"
          type="password"
          autocomplete="${autocomplete}"
          placeholder="${placeholder}"
        />
        <button
          type="button"
          class="password-toggle"
          data-password-toggle="${id}"
          aria-label="Show password"
          title="Show password"
        >◉</button>
      </div>
    `;
  }

  function showLogin(step = 'identifier') {
    const feature = window.IndooneLoginFeature;

    if (!renderScreen('login', feature?.html?.() || fallbackLoginHtml())) {
      return;
    }

    feature?.bind?.(root());
    feature?.showStep?.(step, root());
  }

  function showSignup(step = 'details') {
    const feature = window.IndooneSignupFeature;

    if (!renderScreen('signup', feature?.html?.() || fallbackSignupHtml())) {
      return;
    }

    feature?.bind?.(root());
    feature?.showStep?.(step, root());
  }

  function fallbackLoginHtml() {
    return `
      <div class="auth-copy">
        <h1>Welcome to your Sankalp account</h1>
        <p>The login screen could not be loaded.</p>
      </div>
    `;
  }

  function fallbackSignupHtml() {
    return `
      <div class="auth-copy">
        <h1>Get started with Sankalp</h1>
        <p>The sign-up screen could not be loaded.</p>
      </div>
    `;
  }

  async function showForgotPassword() {
    if (!renderScreen('forgot', '<div class="auth-loading">Loading…</div>')) {
      return;
    }

    const scope = root();

    if (!window.IndooneForgotPassword?.load) {
      setStatus('Forgot Password page is unavailable.', true);
      return;
    }

    try {
      await window.IndooneForgotPassword.load(scope);

      scope
        .querySelector('#forgotPasswordBack')
        ?.addEventListener('click', event => {
          event.preventDefault();
          event.stopPropagation();
          showLogin();
        });
    } catch (error) {
      console.error('Forgot Password page failed to load:', error);

      renderScreen('forgot', `
        <div class="auth-copy">
          <p class="eyebrow">ACCOUNT RECOVERY</p>
          <h1>Forgot password?</h1>
          <p>Unable to load password recovery right now.</p>
        </div>

        <button
          type="button"
          class="secondary"
          id="forgotPasswordLoadBack"
        >
          Back to Login
        </button>
      `);

      $('forgotPasswordLoadBack')?.addEventListener('click', () => showLogin());
    }
  }

  // Whichever identifier field is on screen. Both carry the same text because
  // login.js mirrors them, so either one is the right answer.
  function readLoginIdentifier() {
    const scope = root();
    const stepThree = scope.querySelector('#loginPasswordIdentifier');

    if (stepThree && !stepThree.closest('[data-login-step]')?.hidden) {
      return stepThree.value;
    }

    return scope.querySelector('#loginIdentifier')?.value || '';
  }

  async function run(button) {
    if (!button || busy || button.disabled) return;

    const action = button.dataset.authAction;
    const auth = window.IndooneFirebaseAuth;
    const scope = root();

    if (action === 'signup') return showSignup();
    if (action === 'login') return showLogin();
    if (action === 'forgot-password') return showForgotPassword();
    if (action === 'login-back') return showLogin('identifier');
    if (action === 'signup-back') return showSignup('details');

    if (!auth) {
      setStatus('Authentication service is still loading. Please try again.', true);
      return;
    }

    busy = true;
    const previous = button.textContent;

    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    setStatus('');

    try {
      if (action === 'login-continue-otp') {
        button.textContent = 'Sending…';
        const email = await auth.startLoginOtp(readLoginIdentifier());
        window.IndooneLoginFeature?.otpEmail?.(email, scope);
        showLogin('otp');
      } else if (action === 'login-continue-password') {
        showLogin('password');
      } else if (action === 'login-verify') {
        button.textContent = 'Verifying…';
        await auth.verifyLoginOtp(scope.querySelector('#loginOtp')?.value);
      } else if (action === 'login-resend') {
        button.textContent = 'Sending…';
        const email = await auth.resendLoginOtp();
        window.IndooneLoginFeature?.otpEmail?.(email, scope);
        setStatus('New OTP sent to your email.');
      } else if (action === 'login-complete') {
        button.textContent = 'Logging in…';
        await auth.loginWithPassword(
          readLoginIdentifier(),
          scope.querySelector('#loginPassword')?.value
        );
      } else if (action === 'signup-submit') {
        button.textContent = 'Sending OTP…';
        const email = await auth.startSignup(
          window.IndooneSignupFeature?.values?.(scope) || {}
        );
        window.IndooneSignupFeature?.otpEmail?.(email, scope);
        showSignup('otp');
      } else if (action === 'signup-password') {
        button.textContent = 'Creating account…';
        await auth.createSignupWithPassword(
          window.IndooneSignupFeature?.values?.(scope) || {}
        );
      } else if (action === 'signup-verify') {
        button.textContent = 'Verifying…';
        await auth.finishSignupAfterOtp(
          scope.querySelector('#signupOtp')?.value
        );
      } else if (action === 'signup-resend') {
        button.textContent = 'Sending…';
        const email = await auth.resendSignupOtp();
        window.IndooneSignupFeature?.otpEmail?.(email, scope);
        setStatus('New OTP sent to your email.');
      }
    } catch (error) {
      const message =
        typeof auth.errorMessage === 'function'
          ? auth.errorMessage(error)
          : error?.message || 'Authentication failed.';

      setStatus(message, true);
      console.error('Indoone auth action failed:', error);
    } finally {
      if (button.isConnected) {
        button.textContent = previous;
        button.disabled = false;
        button.removeAttribute('aria-busy');
      }

      busy = false;
      window.IndooneLoginFeature?.applyRules?.(scope);
      window.IndooneSignupFeature?.applyRules?.(scope);
    }
  }

  function bindButtons(scope) {
    scope
      .querySelectorAll('button[data-auth-action]')
      .forEach(button => {
        if (button.dataset.authBound === 'true') return;

        button.dataset.authBound = 'true';

        button.addEventListener('click', event => {
          event.preventDefault();
          event.stopPropagation();
          void run(button);
        });
      });
  }

  function close() {
    $('overlay')?.classList.remove(AUTH_CLASS);
    $('overlay')?.classList.add('hidden');

    // #modal must never carry the auth class. Clear it here as well, so a
    // session that was left over from an older build cannot keep painting the
    // menu and Settings pages beige.
    $('modal')?.classList.remove(AUTH_CLASS);

    if (host()) {
      host().innerHTML = '';
    }

    document.body.classList.remove('auth-open', 'auth-pending');
    currentScreen = null;
  }

  const api = {
    showLogin,
    showSignup,
    showForgotPassword,
    close
  };

  // Publish the one implementation on both names, so any code that still
  // reaches for either gets the same object.
  window.__indooneCanonicalAuthUI = api;
  window.IndooneAuthUI = api;

  function init() {
    if (localStorage.getItem('indoone_otp_verified_uid')) return;

    // A session can already be live by the time this runs - Firebase restores
    // it asynchronously - so never raise the sign-in screen over a signed-in
    // user. events.js takes it down again if it slips through, but the right
    // place to not do it is here.
    if (window.IndooneFirebase?.auth?.currentUser) return;

    showLogin();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
