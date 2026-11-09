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

  function errorMessage(error) {
    const map = {
      'auth/invalid-email': 'Enter a valid email address.',
      'auth/user-not-found': 'No Sankalp account was found.',
      'auth/too-many-requests': 'Too many attempts. Please try again later.',
      'auth/network-request-failed': 'Network error. Check your connection and try again.',
      'auth/weak-password': 'Password should be at least 6 characters.'
    };

    return map[error?.code] || error?.message || 'Unable to continue password recovery.';
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
    const email = String(input?.value || '').trim().toLowerCase();
    const verification = window.IndooneIndoVerification;

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

  function bind(root) {
    if (!root || root.dataset.forgotPasswordBound === 'true') return;

    const continueButton = root.querySelector('#forgotPasswordContinue');
    const verifyButton = root.querySelector('#forgotPasswordVerify');
    const saveButton = root.querySelector('#forgotPasswordSave');

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
