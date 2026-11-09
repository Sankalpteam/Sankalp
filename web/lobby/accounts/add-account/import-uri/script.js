(() => {
  const MARKUP_URL =
    'lobby/accounts/add-account/import-uri/index.html?v=20261018j';
  const STYLE_URL =
    'lobby/accounts/add-account/import-uri/style.css?v=20261018j';

  function loadStyle() {
    const baseUrl = STYLE_URL.split('?')[0];

    if (
      document.querySelector(
        `link[href^="${baseUrl}"]`
      )
    ) {
      return;
    }

    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = STYLE_URL;
    document.head.appendChild(link);
  }

  async function loadMarkup() {
    const response = await fetch(MARKUP_URL, {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Import page could not be loaded.');
    }

    return response.text();
  }

  function setStatus(message) {
    const status = document.getElementById(
      'otpAuthImportStatus'
    );

    if (status) {
      status.textContent = message;
    }
  }

  function bind(root) {
    root
      .querySelector('[data-import-action="back"]')
      ?.addEventListener('click', event => {
        event.preventDefault();
        window.IndooneAddAccount?.showMenu?.({
          push: true
        });
      });

    root
      .querySelector('#otpAuthImportForm')
      ?.addEventListener('submit', event => {
        event.preventDefault();

        const value = document
          .getElementById('otpAuthUri')
          ?.value.trim();

        if (!value) {
          setStatus('Paste an OTPAUTH URI first.');
          return;
        }

        try {
          const parsed = TOTP.parseOtpAuth(value);

          if (!parsed.secret) {
            throw new Error('Missing TOTP secret.');
          }

          const zoho =
            window.IndooneZoho?.detect(
              parsed.issuer,
              parsed.label
            ) || null;

          window.IndooneAddAccount?.showManual?.({
            push: true,
            prefill: {
              name:
                zoho?.service ||
                parsed.issuer ||
                parsed.label ||
                'Account',
              email: parsed.label || '',
              secret: parsed.secret,
              algorithm: parsed.algorithm,
              digits: parsed.digits,
              period: parsed.period,
              provider: zoho?.provider || '',
              service: zoho?.service || ''
            }
          });
        } catch (_) {
          setStatus(
            'Invalid TOTP OTPAUTH data. Check the URI and try again.'
          );
        }
      });
  }

  async function render(mount) {
    loadStyle();

    try {
      mount.innerHTML = await loadMarkup();
      bind(mount);
    } catch (error) {
      mount.innerHTML = `
        <section class="add-account-child import-page">
          <button
            type="button"
            class="page-back"
            data-import-action="back"
          >
            <span aria-hidden="true">‹</span>
            <span>Back</span>
          </button>
          <h1>
            Import OTPAUTH
          </h1>
          <p>
            Unable to load the import page.
          </p>
        </section>
      `;
      bind(mount);
      console.error(
        'Sankalp Add Account Import feature failed:',
        error
      );
    }
  }

  window.IndooneAddAccountImport = { render };
})();
