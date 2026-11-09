// The login screen, built from LoginScreen.kt.
//
// Three steps, one on screen at a time, with the app's own texts and motion.
//
// The identifier is ONE value in the app, shared by the entry step and the
// password step. Here the two fields mirror each other, so whatever is typed on
// one is already there on the other - nothing has to be copied across when the
// step changes, and nothing is lost.
(() => {
  const WELCOME_LINES = [
    { text: 'Welcome to your Sankalp account', gradient: false },
    { text: 'Continue with your Sankalp account', gradient: true }
  ];

  function logoMarkup() {
    return '<span class="auth-mark" aria-label="Sankalp logo"></span>';
  }

  function mobileField() {
    return `
      <div class="mobile-field">
        <span
          id="loginMobilePrefix"
          class="mobile-prefix"
          hidden
        >+91</span>
        <input
          id="loginIdentifier"
          name="login-identifier"
          type="text"
          inputmode="text"
          autocomplete="username"
          autocapitalize="none"
          spellcheck="false"
          maxlength="320"
          placeholder="you@example.com or 98765 43210"
        />
      </div>
    `;
  }

  function passwordField() {
    return `
      <div class="password-wrap">
        <input
          id="loginPassword"
          name="loginPassword"
          type="password"
          autocomplete="current-password"
          placeholder="Enter your password"
        />
        <button
          type="button"
          class="password-toggle"
          data-password-toggle="loginPassword"
          aria-label="Show password"
          title="Show password"
        >◉</button>
      </div>
    `;
  }

  function html() {
    return `
      <div class="auth-brand">
        ${logoMarkup()}
        <div>
          <strong>Sankalp</strong>
          <small>Authenticator</small>
        </div>
      </div>

      <div class="auth-step" data-login-step="identifier">
        <h1
          class="auth-welcome"
          id="loginWelcome"
        ></h1>

        <div class="field">
          <label>EMAIL OR MOBILE NUMBER</label>
          ${mobileField()}
        </div>

        <div class="auth-button-row">
          <button
            type="button"
            class="primary auth-action-button"
            data-auth-action="login-continue-otp"
          >
            Continue with OTP
          </button>

          <button
            type="button"
            class="primary auth-action-button"
            data-auth-action="login-continue-password"
          >
            Continue with Password
          </button>
        </div>

        <button
          type="button"
          class="secondary auth-green auth-action-button"
          data-auth-action="signup"
        >
          Create Account
        </button>
      </div>

      <div class="auth-step" data-login-step="otp" hidden>
        <button
          type="button"
          class="auth-back auth-action-button"
          data-auth-action="login-back"
        >
          ← Back
        </button>

        <h1 class="auth-heading">Enter OTP</h1>

        <p class="auth-note">
          We have sent a 6-digit OTP to<br />
          <strong id="loginOtpEmail"></strong>
        </p>

        <div class="field">
          <label>VERIFICATION OTP</label>
          <input
            id="loginOtp"
            name="one-time-code"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            placeholder="Enter 6-digit OTP"
          />
        </div>

        <button
          type="button"
          class="primary auth-action-button"
          data-auth-action="login-verify"
        >
          Verify &amp; Login
        </button>

        <button
          type="button"
          class="secondary auth-action-button"
          data-auth-action="login-resend"
        >
          Resend OTP
        </button>
      </div>

      <div class="auth-step" data-login-step="password" hidden>
        <button
          type="button"
          class="auth-back auth-action-button"
          data-auth-action="login-back"
        >
          ← Back
        </button>

        <h1 class="auth-heading">Enter Password</h1>

        <p class="auth-note auth-note-gradient">
          Continue with your Sankalp account
        </p>

        <div class="field">
          <label>EMAIL OR MOBILE NUMBER</label>
          <input
            id="loginPasswordIdentifier"
            name="login-password-identifier"
            type="text"
            inputmode="text"
            autocomplete="username"
            autocapitalize="none"
            spellcheck="false"
            maxlength="320"
            placeholder="you@example.com or 98765 43210"
          />
        </div>

        <div class="field">
          <label>PASSWORD</label>
          ${passwordField()}
        </div>

        <button
          type="button"
          class="primary auth-action-button"
          data-auth-action="login-complete"
        >
          Complete Login
        </button>
      </div>

      <div
        class="auth-dots"
        id="loginDots"
        aria-hidden="true"
      >
        <span data-dot="identifier"></span>
        <span data-dot="otp"></span>
        <span data-dot="password"></span>
      </div>
    `;
  }

  // ------------------------------------------------------------- the wiring

  function buttonFor(root, action) {
    return root.querySelector(`[data-auth-action="${action}"]`);
  }

  // The app keeps one identifier. These two fields are that one value.
  function bindMirror(root) {
    const stepOne = root.querySelector('#loginIdentifier');
    const stepThree = root.querySelector('#loginPasswordIdentifier');

    if (!stepOne || !stepThree) return;

    if (stepOne.dataset.mirrorBound !== 'true') {
      stepOne.dataset.mirrorBound = 'true';
      stepThree.dataset.mirrorBound = 'true';

      stepOne.addEventListener('input', () => {
        stepThree.value = stepOne.value;
        applyRules(root);
      });

      stepThree.addEventListener('input', () => {
        stepOne.value = stepThree.value;
        applyRules(root);
      });
    }
  }

  // The app only lets Verify & Login be pressed once six digits are in, and
  // Complete Login once both fields have something in them. The two Continue
  // buttons on the identifier step are gated on the identifier being filled in
  // (LoginScreen.kt: actionEnabled = !busy && identifier.isNotBlank()).
  function applyRules(root) {
    const otp = root.querySelector('#loginOtp');
    const verify = buttonFor(root, 'login-verify');

    if (verify && otp) {
      verify.disabled = otp.value.replace(/\D/g, '').length !== 6;
    }

    const identifier = root.querySelector('#loginIdentifier');
    const passwordIdentifier = root.querySelector('#loginPasswordIdentifier');
    const password = root.querySelector('#loginPassword');
    const complete = buttonFor(root, 'login-complete');

    if (complete) {
      const identifierValue = (
        passwordIdentifier?.value ||
        identifier?.value ||
        ''
      ).trim();

      complete.disabled = !identifierValue || !(password?.value || '');
    }

    const identifierBlank = !(
      passwordIdentifier?.value ||
      identifier?.value ||
      ''
    ).trim();

    ['login-continue-otp', 'login-continue-password'].forEach(action => {
      const button = buttonFor(root, action);

      if (button) {
        button.disabled = identifierBlank;
      }
    });
  }

  function bind(root) {
    if (!root?.querySelector) return;

    bindMirror(root);

    ['#loginOtp', '#loginPassword', '#loginIdentifier',
      '#loginPasswordIdentifier'].forEach(selector => {
      const input = root.querySelector(selector);

      if (input && input.dataset.rulesBound !== 'true') {
        input.dataset.rulesBound = 'true';
        input.addEventListener('input', () => applyRules(root));
      }
    });

    applyRules(root);
  }

  // ------------------------------------------------------- the welcome line

  // The app swaps the welcome line every two seconds: the outgoing line fades
  // out while sliding left, the incoming one fades in from the right.
  let welcomeTimer = null;
  let welcomeIndex = 0;

  function paintWelcome(root) {
    const node = root.querySelector('#loginWelcome');

    if (!node) return;

    const line = WELCOME_LINES[welcomeIndex % WELCOME_LINES.length];
    node.textContent = line.text;
    node.classList.toggle('is-gradient', line.gradient);
    node.classList.remove('is-entering');
    void node.offsetWidth;
    node.classList.add('is-entering');
  }

  function startWelcome(root) {
    stopWelcome();
    welcomeIndex = 0;
    paintWelcome(root);
    welcomeTimer = setInterval(() => {
      welcomeIndex += 1;
      paintWelcome(root);
    }, 2000);
  }

  function stopWelcome() {
    if (welcomeTimer) {
      clearInterval(welcomeTimer);
      welcomeTimer = null;
    }
  }

  // ------------------------------------------------------------- the steps

  function showStep(step, root = document) {
    root.querySelectorAll('[data-login-step]').forEach(panel => {
      panel.hidden = panel.dataset.loginStep !== step;
    });

    root.querySelectorAll('#loginDots [data-dot]').forEach(dot => {
      dot.classList.toggle('on', dot.dataset.dot === step);
    });

    const focus = {
      identifier: '#loginIdentifier',
      otp: '#loginOtp',
      password: '#loginPassword'
    }[step];

    root.querySelector(focus)?.focus();

    if (step === 'identifier') {
      startWelcome(root);
    } else {
      stopWelcome();
    }

    applyRules(root);
  }

  function otpEmail(email, root = document) {
    const node = root.querySelector('#loginOtpEmail');

    if (node) {
      node.textContent = email || '';
    }
  }

  window.IndooneLoginFeature = {
    html,
    bind,
    showStep,
    otpEmail,
    applyRules,
    stopWelcome,
    show: () => window.IndooneAuthUI?.showLogin?.()
  };
})();
