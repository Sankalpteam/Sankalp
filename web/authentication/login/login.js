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

        <!-- The same agreement the signup screen asks for, and asked here too: a person
             coming back to an account they already have still has to accept the terms they are
             signing in under. Nothing is recorded from this box - the record belongs to signup,
             and writing one here would be a record of an agreement made on a different screen.
             It sits above the buttons because it gates them. -->
        <div class="auth-consent">
          <label class="auth-consent-row">
            <input type="checkbox" id="loginConsent" />
            <span>
              I am 18 or older and I agree to the
              <a href="/legal/terms-of-use.html" target="_blank" rel="noopener">Terms of Use</a>
              and the
              <a href="/legal/privacy-policy.html" target="_blank" rel="noopener">Privacy Policy</a>.
            </span>
          </label>
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
  // Sixty seconds between OTP sends, because the thing that happens without it is worse than
  // waiting: a person taps Resend three times, Firebase answers TOO_MANY_REQUESTS, and the
  // address is locked out for far longer than a minute. The countdown is shown on the button
  // so the wait is visible instead of the button looking broken.
  const RESEND_COOLDOWN_MS = 60_000;
  let resendUntil = 0;
  let resendTicker = null;

  function resendSecondsLeft() {
    return Math.max(0, Math.ceil((resendUntil - Date.now()) / 1000));
  }

  function startResendCooldown(root = document) {
    resendUntil = Date.now() + RESEND_COOLDOWN_MS;
    if (resendTicker) return;
    resendTicker = window.setInterval(() => {
      if (resendSecondsLeft() <= 0) {
        window.clearInterval(resendTicker);
        resendTicker = null;
        resendUntil = 0;
      }
      applyRules(document);
    }, 1000);
    applyRules(root);
  }

  function applyRules(root) {
    const otp = root.querySelector('#loginOtp');
    const verify = buttonFor(root, 'login-verify');

    // Every action on this screen waits for the box. It is on screen the whole time, so it
    // can be unticked on the code step too - and if it can be unticked, the buttons have to
    // answer it there as well.
    const agreed = Boolean(root.querySelector('#loginConsent')?.checked);

    // The resend button: disabled and counting down, or ready again.
    const resend = buttonFor(root, 'login-resend');
    if (resend) {
      const left = resendSecondsLeft();
      resend.disabled = left > 0;
      resend.textContent = left > 0 ? `Resend in ${left}s` : 'Resend OTP';
      resend.setAttribute('aria-live', 'polite');
    }

    if (verify && otp) {
      verify.disabled = otp.value.replace(/\D/g, '').length !== 6 || !agreed;
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

      complete.disabled = !identifierValue || !(password?.value || '') || !agreed;
    }

    const identifierBlank = !(
      passwordIdentifier?.value ||
      identifier?.value ||
      ''
    ).trim();

    ['login-continue-otp', 'login-continue-password'].forEach(action => {
      const button = buttonFor(root, action);

      if (button) {
        button.disabled = identifierBlank || !agreed;
      }
    });
  }

  function bind(root) {
    if (!root?.querySelector) return;

    bindMirror(root);

    const consent = root.querySelector('#loginConsent');
    if (consent && consent.dataset.consentBound !== 'true') {
      consent.dataset.consentBound = 'true';
      consent.addEventListener('change', () => applyRules(root));
    }

    // The code field holds digits and nothing else, the way the signup screen's does. Left
    // alone it accepted "12a34b" - six characters, four digits, and a Verify button that
    // stayed disabled with nothing on screen explaining why.
    const otpInput = root.querySelector('#loginOtp');
    if (otpInput && otpInput.dataset.digitFilterBound !== 'true') {
      otpInput.dataset.digitFilterBound = 'true';
      otpInput.addEventListener('input', () => {
        const digits = otpInput.value.replace(/\D/g, '').slice(0, 6);
        const shared = window.IndooneAuthUI?.setFilteredValue;
        if (shared) {
          shared(otpInput, digits);
        } else if (otpInput.value !== digits) {
          otpInput.value = digits;
        }
        applyRules(root);
      });
    }

    // Enter submits, the way it does on the signup screen. There is no <form> here either.
    const onEnter = event => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      const otpPanel = root.querySelector('[data-login-step="otp"]');
      const passwordPanel = root.querySelector('[data-login-step="password"]');
      const onCodeStep = otpPanel && !otpPanel.hidden;
      const onPasswordStep = passwordPanel && !passwordPanel.hidden;
      const action = onCodeStep
        ? 'login-verify'
        : onPasswordStep
          ? 'login-complete'
          : 'login-continue-otp';
      const target = root.querySelector(`[data-auth-action="${action}"]`);
      if (target && !target.disabled) target.click();
    };

    // #loginOtp is not in this list: its own filter above already re-evaluates the form.
    ['#loginPassword', '#loginIdentifier', '#loginPasswordIdentifier']
      .forEach(selector => {
        const input = root.querySelector(selector);

        if (input && input.dataset.rulesBound !== 'true') {
          input.dataset.rulesBound = 'true';
          input.addEventListener('input', () => applyRules(root));
        }
      });

    ['#loginIdentifier', '#loginPasswordIdentifier', '#loginPassword', '#loginOtp']
      .forEach(selector => {
        const input = root.querySelector(selector);

        if (input && input.dataset.enterBound !== 'true') {
          input.dataset.enterBound = 'true';
          input.addEventListener('keydown', onEnter);
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
    startResendCooldown,
    stopWelcome,
    show: () => window.IndooneAuthUI?.showLogin?.()
  };
})();
