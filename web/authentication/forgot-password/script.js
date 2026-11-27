window.IndooneForgotPassword = (() => {
  const MARKUP_URL = 'authentication/forgot-password/index.html?v=20261018b';
  let state = {
    email: '',
    challengeId: '',
    resetToken: ''
  };

  function setStatus(root, message = '', error = false) {
    const status = root
      ?.closest('.auth-page')
      ?.querySelector('#authStatus');

    if (!status) return;

    status.textContent = message;
    status.hidden = !message;
    status.dataset.error = error ? 'true' : 'false';
  }

  // The same one map as the rest of the auth screens (firebase-auth.js). A third copy lived
  // here and had already drifted - no 'auth/user-disabled', and no 'auth/invalid-credential'
  // at all, so a wrong password on recovery said something different from a wrong password
  // on sign-in.
  function errorMessage(error) {
    const base = window.IndooneFirebaseAuthBase?.errorMessage;
    if (typeof base === 'function') return base(error);
    return window.sankalpError?.fromError(error) || 'Unable to continue password recovery.';
  }

  function setBusy(button, text) {
    if (!button) return '';

    const previousText = button.textContent;
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    button.textContent = text;
    return previousText;
  }

  function clearBusy(button, previousText) {
    if (!button?.isConnected) return;

    button.disabled = false;
    button.removeAttribute('aria-busy');
    if (previousText) button.textContent = previousText;
  }

  function hideEmailStep(root) {
    const emailField = root.querySelector('#forgotPasswordEmailField');
    const continueButton = root.querySelector('#forgotPasswordContinue');
    if (emailField) emailField.hidden = true;
    if (continueButton) continueButton.hidden = true;
  }

  async function requestOtp(root, button) {
    const input = root.querySelector('#forgotPasswordEmail');
    const raw = String(input?.value || '').trim();
    const verification = window.IndooneIndoVerification;

    if (!raw) {
      setStatus(root, 'Enter your email or mobile number.', true);
      input?.focus();
      return;
    }

    // Recovery used to accept an email address and nothing else, while sign-in accepted
    // either. A person who signed up with a mobile number, forgot the password and still has
    // the mobile in their hand was told "Enter a valid email address" - on the one screen that
    // exists to get them back in. The same resolver login uses turns a mobile into the email
    // the account is filed under.
    let email = '';
    try {
      const resolver = window.IndooneFirebaseAuth?.resolveLoginEmail;
      email = resolver
        ? await resolver(raw)
        : String(raw).trim().toLowerCase();
    } catch (error) {
      setStatus(root, errorMessage(error), true);
      input?.focus();
      return;
    }

    if (!email || !email.includes('@')) {
      setStatus(root, 'Enter a valid email address.', true);
      input?.focus();
      return;
    }

    if (!verification?.requestForgotPasswordOtp) {
      setStatus(root, 'OTP verification service is unavailable. Please try again.', true);
      return;
    }

    const previousText = setBusy(button, 'Sending…');
    setStatus(root);

    try {
      const result = await verification.requestForgotPasswordOtp(email);
      if (!result?.challengeId) {
        throw new Error('OTP service did not return a challenge ID.');
      }

      state = {
        email,
        challengeId: result.challengeId,
        resetToken: ''
      };

      const otpArea = root.querySelector('#forgotPasswordOtpArea');
      const emailLabel = root.querySelector('#forgotPasswordEmailLabel');
      const otpInput = root.querySelector('#forgotPasswordOtp');

      if (!otpArea || !otpInput) {
        throw new Error('OTP input is unavailable. Please reload and try again.');
      }

      if (emailLabel) emailLabel.textContent = email;
      otpArea.hidden = false;
      button.textContent = 'OTP Sent';
      setStatus(root, 'OTP sent. Enter the 6-digit code from your email.');
      // A code just went out, so the resend button waits before offering another.
      startResendCooldown(root);
      otpInput.focus();
    } catch (error) {
      setStatus(root, errorMessage(error), true);
      console.error('Forgot Password OTP request failed:', error);
    } finally {
      if (button.isConnected) {
        button.disabled = false;
        button.removeAttribute('aria-busy');
        if (!state.challengeId) button.textContent = previousText;
      }
    }
  }

  async function verifyOtp(root, button) {
    const input = root.querySelector('#forgotPasswordOtp');
    const otp = String(input?.value || '').replace(/\D/g, '').slice(0, 6);
    const verification = window.IndooneIndoVerification;

    if (!state.email || !state.challengeId) {
      setStatus(root, 'Please request a new OTP first.', true);
      return;
    }

    if (!/^\d{6}$/.test(otp)) {
      setStatus(root, 'Enter the 6-digit OTP.', true);
      input?.focus();
      return;
    }

    if (!verification?.verifyForgotPasswordOtp) {
      setStatus(root, 'OTP verification service is unavailable. Please try again.', true);
      return;
    }

    const previousText = setBusy(button, 'Verifying…');
    setStatus(root);

    try {
      const result = await verification.verifyForgotPasswordOtp({
        email: state.email,
        challengeId: state.challengeId,
        otp
      });

      if (result?.verified === false || !result?.resetToken) {
        throw new Error(result?.error || 'OTP verification failed.');
      }

      state.resetToken = result.resetToken;

      const otpArea = root.querySelector('#forgotPasswordOtpArea');
      const resetArea = root.querySelector('#forgotPasswordResetArea');
      const newPassword = root.querySelector('#forgotPasswordNewPassword');
      const emailInput = root.querySelector('#forgotPasswordEmail');

      if (otpArea) otpArea.hidden = true;
      if (resetArea) resetArea.hidden = false;
      if (emailInput) emailInput.disabled = true;
      hideEmailStep(root);
      setStatus(root, 'OTP verified. Enter your new password.');
      newPassword?.focus();
    } catch (error) {
      setStatus(root, errorMessage(error), true);
      console.error('Forgot Password OTP verification failed:', error);
    } finally {
      clearBusy(button, previousText);
    }
  }

  async function resetPassword(root, button) {
    const newPasswordInput = root.querySelector('#forgotPasswordNewPassword');
    const confirmPasswordInput = root.querySelector('#forgotPasswordConfirmPassword');
    const verification = window.IndooneIndoVerification;
    const newPassword = String(newPasswordInput?.value || '');
    const confirmPassword = String(confirmPasswordInput?.value || '');

    if (!state.email || !state.resetToken) {
      setStatus(root, 'Please verify the OTP first.', true);
      return;
    }

    if (newPassword.length < 6) {
      setStatus(root, 'Password should be at least 6 characters.', true);
      newPasswordInput?.focus();
      return;
    }

    if (newPassword.length > 4096) {
      setStatus(root, 'Password is too long.', true);
      newPasswordInput?.focus();
      return;
    }

    if (newPassword !== confirmPassword) {
      setStatus(root, 'Passwords do not match.', true);
      confirmPasswordInput?.focus();
      return;
    }

    if (!verification?.resetForgotPassword) {
      setStatus(root, 'Password reset service is unavailable. Please try again.', true);
      return;
    }

    const previousText = setBusy(button, 'Saving…');
    setStatus(root);

    try {
      const result = await verification.resetForgotPassword({
        email: state.email,
        resetToken: state.resetToken,
        newPassword
      });

      if (!result?.ok) {
        throw new Error(result?.error || 'Unable to change your password.');
      }

      const resetArea = root.querySelector('#forgotPasswordResetArea');
      if (resetArea) resetArea.hidden = true;
      newPasswordInput.value = '';
      confirmPasswordInput.value = '';
      setStatus(root, 'Password changed successfully. You can now log in with your new password.');
      state = {
        email: '',
        challengeId: '',
        resetToken: ''
      };
    } catch (error) {
      setStatus(root, errorMessage(error), true);
      console.error('Forgot Password reset failed:', error);
    } finally {
      clearBusy(button, previousText);
    }
  }

  // The recovery screen had no way to ask for a second code. A mail that did not arrive left
  // the person reloading the page, which throws away the challenge and starts again. Same
  // sixty seconds as sign-in and sign-up, for the same reason.
  const RESEND_COOLDOWN_MS = 60_000;
  let resendUntil = 0;
  let resendTicker = null;

  function resendSecondsLeft() {
    return Math.max(0, Math.ceil((resendUntil - Date.now()) / 1000));
  }

  function paintResend(root) {
    const button = root.querySelector('#forgotPasswordResend');
    if (!button) return;
    const left = resendSecondsLeft();
    button.disabled = left > 0;
    button.textContent = left > 0 ? `Resend in ${left}s` : 'Resend OTP';
    button.setAttribute('aria-live', 'polite');
  }

  function startResendCooldown(root) {
    resendUntil = Date.now() + RESEND_COOLDOWN_MS;
    paintResend(root);
    if (resendTicker) return;
    resendTicker = window.setInterval(() => {
      if (resendSecondsLeft() <= 0) {
        window.clearInterval(resendTicker);
        resendTicker = null;
        resendUntil = 0;
      }
      paintResend(root);
    }, 1000);
  }

  async function resendOtp(root, button) {
    if (resendSecondsLeft() > 0) return;
    if (!state.challengeId) {
      setStatus(root, 'Request a code first.', true);
      return;
    }

    const previousText = setBusy(button, 'Sending…');
    setStatus(root);

    try {
      const verification = window.IndooneIndoVerification;
      const result = await verification.requestForgotPasswordOtp(state.email);

      if (!result?.challengeId) {
        throw new Error('OTP service did not return a challenge ID.');
      }

      state.challengeId = result.challengeId;
      setStatus(root, 'New OTP sent to your email.');
      startResendCooldown(root);
    } catch (error) {
      setStatus(root, errorMessage(error), true);
    } finally {
      clearBusy(button, previousText);
      paintResend(root);
    }
  }

  function bind(root) {
    if (!root || root.dataset.forgotPasswordBound === 'true') return;

    const continueButton = root.querySelector('#forgotPasswordContinue');
    const verifyButton = root.querySelector('#forgotPasswordVerify');
    const saveButton = root.querySelector('#forgotPasswordSave');
    const resendButton = root.querySelector('#forgotPasswordResend');

    if (!continueButton || !verifyButton || !saveButton) return;

    root.dataset.forgotPasswordBound = 'true';

    continueButton.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      void requestOtp(root, continueButton);
    });

    verifyButton.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      void verifyOtp(root, verifyButton);
    });

    saveButton.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      void resetPassword(root, saveButton);
    });

    resendButton?.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      void resendOtp(root, resendButton);
    });

    const emailInput = root.querySelector('#forgotPasswordEmail');
    emailInput?.addEventListener('keydown', event => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      void requestOtp(root, continueButton);
    });

    const otpInput = root.querySelector('#forgotPasswordOtp');
    otpInput?.addEventListener('input', () => {
      otpInput.value = otpInput.value.replace(/\D/g, '').slice(0, 6);
    });
    otpInput?.addEventListener('keydown', event => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      void verifyOtp(root, verifyButton);
    });

    const confirmPasswordInput = root.querySelector('#forgotPasswordConfirmPassword');
    confirmPasswordInput?.addEventListener('keydown', event => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      void resetPassword(root, saveButton);
    });
  }

  async function load(root) {
    if (!root) return;

    const status = root.querySelector('#authStatus');
    const response = await fetch(MARKUP_URL, { cache: 'no-store' });
    if (!response.ok) {
      throw new Error('Forgot Password page could not be loaded.');
    }

    root.innerHTML = await response.text();

    if (status) root.appendChild(status);
    bind(root);
  }

  return { load };
})();
