// The sign-up screen, built from SignUpScreen.kt.
//
// This is ONE page, not two steps. The email, mobile and password fields stay
// on screen the whole time; when the OTP is sent, the two buttons are replaced
// by a single Resend OTP and the OTP section appears below the fields. The web
// used to hide the whole form and show the OTP on its own, which is not how the
// app does it - and it threw away everything typed.
(() => {
  const TITLE_LINES = [
    { text: 'Get started with', purple: true },
    { text: 'Creating your account', purple: false }
  ];
  const DESCRIPTION =
    'Create your Sankalp account and start asking anything, in Kannada or English.';
  const DESCRIPTION_MS = 2000;

  function logoMarkup() {
    return '<span class="auth-mark" aria-label="Sankalp logo"></span>';
  }

  function mobileField() {
    return `
      <div class="mobile-field">
        <span
          id="signupMobilePrefix"
          class="mobile-prefix"
          hidden
        >+91</span>
        <input
          id="signupMobile"
          name="phone"
          type="tel"
          inputmode="tel"
          autocomplete="tel"
          autocapitalize="none"
          spellcheck="false"
          maxlength="13"
          placeholder="98765 43210"
        />
      </div>
    `;
  }

  function passwordField() {
    return `
      <div class="password-wrap">
        <input
          id="signupPassword"
          name="signupPassword"
          type="password"
          autocomplete="new-password"
          placeholder="Create a strong password"
        />
        <button
          type="button"
          class="password-toggle"
          data-password-toggle="signupPassword"
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

      <div class="auth-title-box">
        <h1
          class="auth-heading"
          id="signupTitle"
        ></h1>
      </div>

      <p
        class="auth-note"
        id="signupDescription"
      ></p>

      <div class="field">
        <label>EMAIL ID</label>
        <input
          id="signupEmail"
          name="email"
          type="email"
          autocomplete="email"
          autocapitalize="none"
          spellcheck="false"
          placeholder="you@example.com"
        />
      </div>

      <div class="field">
        <label>MOBILE NUMBER</label>
        ${mobileField()}
      </div>

      <div class="field">
        <label>PASSWORD</label>
        ${passwordField()}
      </div>

      <!-- The recovery screen has asked for the new password twice since it existed; signup,
           which is where the password is chosen for the first time, asked once. A typo here
           makes an account whose password nobody knows. -->
      <div class="field">
        <label>CONFIRM PASSWORD</label>
        <div class="password-wrap">
          <input
            id="signupConfirmPassword"
            name="signupConfirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="Type it again"
          />
          <button
            type="button"
            class="password-toggle"
            data-password-toggle="signupConfirmPassword"
            aria-label="Show password"
            title="Show password"
          >◉</button>
        </div>
      </div>

      <!-- The agreement, once, at the moment an account is created. The two documents are
           the ones already published at /legal/ - no new page, so a change to either reaches
           this screen and every other place that links it. -->
      <div
        class="auth-step auth-signup-actions"
        data-signup-step="details"
      >
        <button
          type="button"
          class="primary auth-action-button"
          data-auth-action="signup-submit"
        >
          Continue with OTP
        </button>

        <button
          type="button"
          class="primary auth-action-button"
          data-auth-action="signup-password"
        >
          Continue with Password
        </button>
      </div>

      <div
        class="auth-step auth-signup-otp"
        data-signup-step="otp"
        hidden
      >
        <!-- The handler for this has existed in auth-page.js since the screen was written and
             nothing ever had the attribute, so it was dead code: a person who typed their
             email wrong could only go back to Login and start again. -->
        <button
          type="button"
          class="auth-back auth-action-button"
          data-auth-action="signup-back"
        >
          ← Back
        </button>

        <p class="auth-note auth-note-tight">
          OTP sent to <strong id="signupOtpEmail"></strong>
        </p>

        <div class="field">
          <label>VERIFICATION OTP</label>
          <input
            id="signupOtp"
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
          data-auth-action="signup-verify"
        >
          Verify &amp; Create Account
        </button>

        <!-- Resend belongs under Verify: the screen is asking for a code, and the way to get
             another one comes after the way to use the one you have. It sat above the field,
             which read as the first thing to do. -->
        <button
          type="button"
          class="secondary auth-action-button"
          data-auth-action="signup-resend"
        >
          Resend OTP
        </button>
      </div>

      <!-- Shown on both steps, and placed under them: it sat between the fields and the
           step panels, so on the code step the box appeared above the Back button. -->
      <div class="auth-consent">
        <label class="auth-consent-row">
          <input type="checkbox" id="signupConsent" data-signup-consent />
          <span>
            I am 18 or older and I agree to the
            <a href="/legal/terms-of-use.html" target="_blank" rel="noopener">Terms of Use</a>
            and the
            <a href="/legal/privacy-policy.html" target="_blank" rel="noopener">Privacy Policy</a>.
          </span>
        </label>
      </div>

      <button
        type="button"
        class="secondary auth-green auth-signup-login auth-action-button"
        data-auth-action="login"
      >
        Already have an account? Login
      </button>

      <p class="auth-footer">
        Continue with OTP verifies your email. Continue with Password creates
        the account directly using your password.
      </p>
    `;
  }

  // ------------------------------------------------------------- the wiring

  function buttonFor(root, action) {
    return root.querySelector(`[data-auth-action="${action}"]`);
  }

  // The app keeps only digits in the mobile field and stops at ten.
  function bindMobileFilter(root) {
    const mobile = root.querySelector('#signupMobile');

    if (!mobile || mobile.dataset.digitFilterBound === 'true') return;

    mobile.dataset.digitFilterBound = 'true';

    const consent = root.querySelector('#signupConsent');
    if (consent && consent.dataset.consentBound !== 'true') {
      consent.dataset.consentBound = 'true';
      consent.addEventListener('change', () => applyRules(root));
    }

    mobile.addEventListener('input', () => {
      const digits = mobile.value.replace(/\D/g, '').slice(0, 10);

      if (mobile.value !== digits) {
        mobile.value = digits;
      }

      applyRules(root);
    });
  }

  // The app enables both buttons only once the email and mobile are filled and
  // the password is six characters or more; and Verify only at six digits.
  // The same sixty seconds as the login screen, for the same reason: three taps in ten
  // seconds is what a person does when the mail has not arrived, and Firebase answers the
  // third with TOO_MANY_REQUESTS - a lockout far longer than the wait this imposes.
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
    const resend = buttonFor(root, 'signup-resend');
    if (resend) {
      const left = resendSecondsLeft();
      resend.disabled = left > 0;
      resend.textContent = left > 0 ? `Resend in ${left}s` : 'Resend OTP';
      resend.setAttribute('aria-live', 'polite');
    }

    const email = (root.querySelector('#signupEmail')?.value || '').trim();
    const mobile = (root.querySelector('#signupMobile')?.value || '').trim();
    const password = root.querySelector('#signupPassword')?.value || '';
    const confirmPassword = root.querySelector('#signupConfirmPassword')?.value || '';
    // An Indian mobile is ten digits. The field strips everything else and caps at ten, but
    // nothing required the tenth, so one digit enabled both buttons and the person met the
    // service's refusal instead of a disabled button. (The service checks the format too, so
    // no bad number was ever stored - this is about when the answer arrives.)
    const mobileDigits = mobile.replace(/\D/g, '');
    const agreed = Boolean(root.querySelector('#signupConsent')?.checked);
    const ready =
      Boolean(email) &&
      mobileDigits.length === 10 &&
      password.length >= 6 &&
      password === confirmPassword &&
      agreed;

    ['signup-submit', 'signup-password'].forEach(action => {
      const button = buttonFor(root, action);

      if (button) {
        button.disabled = !ready;
      }
    });

    const otp = root.querySelector('#signupOtp');
    const verify = buttonFor(root, 'signup-verify');

    if (verify && otp) {
      // The box is still on this screen and can still be unticked. Verify honours it, so a
      // withdrawn tick cannot end in a consent record that says the opposite.
      const stillAgreed = Boolean(root.querySelector('#signupConsent')?.checked);
      verify.disabled = otp.value.replace(/\D/g, '').length !== 6 || !stillAgreed;
    }
  }

  function bind(root) {
    if (!root?.querySelector) return;

    bindMobileFilter(root);

    ['#signupEmail', '#signupMobile', '#signupPassword', '#signupOtp']
      .forEach(selector => {
        const input = root.querySelector(selector);

        if (input && input.dataset.rulesBound !== 'true') {
          input.dataset.rulesBound = 'true';
          input.addEventListener('input', () => applyRules(root));
        }
      });

    applyRules(root);
  }

  // -------------------------------------------------- the title and the type

  // The app alternates the title every two seconds, and types the description
  // out over the same two seconds.
  let titleTimer = null;
  let typeTimer = null;
  let titleIndex = 0;

  function paintTitle(root) {
    const node = root.querySelector('#signupTitle');

    if (!node) return;

    const line = TITLE_LINES[titleIndex % TITLE_LINES.length];
    node.textContent = line.text;
    node.classList.toggle('is-purple', line.purple);
  }

  function startTitle(root) {
    stopTitle();
    titleIndex = 0;
    paintTitle(root);
    titleTimer = setInterval(() => {
      titleIndex += 1;
      paintTitle(root);
    }, 2000);
  }

  function stopTitle() {
    if (titleTimer) {
      clearInterval(titleTimer);
      titleTimer = null;
    }
  }

  function startTyping(root) {
    stopTyping();

    const node = root.querySelector('#signupDescription');

    if (!node) return;

    let shown = 0;
    node.textContent = '';
    const step = Math.max(DESCRIPTION_MS / DESCRIPTION.length, 16);

    typeTimer = setInterval(() => {
      shown += 1;
      node.textContent = DESCRIPTION.slice(0, shown);

      if (shown >= DESCRIPTION.length) {
        stopTyping();
      }
    }, step);
  }

  function stopTyping() {
    if (typeTimer) {
      clearInterval(typeTimer);
      typeTimer = null;
    }
  }

  // ------------------------------------------------------------- the steps

  // Only the actions block and the OTP block swap. The fields never move.
  function showStep(step, root = document) {
    root.querySelectorAll('[data-signup-step]').forEach(panel => {
      panel.hidden = panel.dataset.signupStep !== step;
    });

    if (step === 'otp') {
      stopTitle();
      stopTyping();
      root.querySelector('#signupOtp')?.focus();
    } else {
      startTitle(root);
      startTyping(root);
      root.querySelector('#signupEmail')?.focus();
    }

    applyRules(root);
  }

  function otpEmail(email, root = document) {
    const node = root.querySelector('#signupOtpEmail');

    if (node) {
      node.textContent = email || '';
    }
  }

  function values(root = document) {
    return {
      email: root.querySelector('#signupEmail')?.value || '',
      mobile: root.querySelector('#signupMobile')?.value || '',
      password: root.querySelector('#signupPassword')?.value || '',
      confirmPassword: root.querySelector('#signupConfirmPassword')?.value || '',
      agreed: Boolean(root.querySelector('#signupConsent')?.checked)
    };
  }

  window.IndooneSignupFeature = {
    html,
    bind,
    showStep,
    otpEmail,
    values,
    applyRules,
    startResendCooldown,
    show: () => window.IndooneAuthUI?.showSignup?.()
  };
})();
