// The login screen, built from LoginScreen.kt.
//
// Two steps, one on screen at a time: the identifier, then the OTP. There is no password step -
// signing in is an email or a mobile number and a six-digit code, nothing to remember and
// nothing to reset.
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

      <div
        class="auth-dots"
        id="loginDots"
        aria-hidden="true"
      >
        <span data-dot="identifier"></span>
        <span data-dot="otp"></span>
      </div>
    `;
  }

  // ------------------------------------------------------------- the wiring

  function buttonFor(root, action) {
    return root.querySelector(`[data-auth-action="${action}"]`);
  }

  function applyRules(root) {
    const otp = root.querySelector('#loginOtp');
    const verify = buttonFor(root, 'login-verify');

    if (verify && otp) {
      verify.disabled = otp.value.replace(/\D/g, '').length !== 6;
    }

    const identifier = root.querySelector('#loginIdentifier');
    const identifierBlank = !(identifier?.value || '').trim();
    const button = buttonFor(root, 'login-continue-otp');

    if (button) {
      button.disabled = identifierBlank;
    }
  }

  function bind(root) {
    if (!root?.querySelector) return;

    ['#loginOtp', '#loginIdentifier'].forEach(selector => {
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
      otp: '#loginOtp'
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
