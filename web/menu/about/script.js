window.initMenuAbout = function () {
  const modal = document.getElementById('modal');

  // MenuAboutScreen.kt draws six rows, each with a 1px #EEE8F4 divider above
  // it. It has no logo/tagline block and no copyright footer, so neither is
  // drawn here - they used to be, and this initializer overwrites the fragment,
  // so they were what actually rendered.
  modal.innerHTML = `
    <div
      class="modal-head"
    >
      <h2>
        About Sankalp
      </h2>
      <button
        class="close-btn"
        data-close
      >
        ×
      </button>
    </div>

    <div class="menu-about">
      <div
        class="settings-row"
      >
        <span>
          <b>
            About Sankalp
          </b>
          <small>
            Securely generate and manage time-based one-time passwords for your accounts.
          </small>
        </span>
      </div>

      <div
        class="settings-row"
      >
        <span>
          <b>
            Sync
          </b>
          <small>
            Secure cloud sync across your signed-in devices
          </small>
        </span>
      </div>

      <div
        class="settings-row"
      >
        <span>
          <b>
            OTP Standard
          </b>
          <small>
            TOTP • 6/8 digits • SHA-1 / SHA-256 / SHA-512
          </small>
        </span>
      </div>

      <div
        class="settings-row"
      >
        <span>
          <b>
            Account Storage
          </b>
          <small>
            Cloud synced with your Sankalp account
          </small>
        </span>
      </div>

      <div
        class="settings-row"
      >
        <span>
          <b>
            Legal
          </b>
          <small>
            Privacy Policy • Terms of Service
          </small>
        </span>
      </div>

      <div
        class="settings-row"
      >
        <span>
          <b>
            Licenses
          </b>
          <small>
            Open Source Licenses
          </small>
        </span>
      </div>
    </div>
  `;
};
