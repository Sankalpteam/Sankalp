(() => {
  const MENU_MARKUP_URL =
    'lobby/accounts/add-account/index.html?v=20261018j';
  const MENU_STYLE_URL =
    'lobby/accounts/add-account/style.css?v=20261018j';

  const FEATURES = {
    qr: {
      script:
        'lobby/accounts/add-account/scan-qr/account-details/script.js?v=20261018j',
      globalName: 'IndooneAddAccountQr'
    },
    manual: {
      script:
        'lobby/accounts/add-account/enter-setup-key/script.js?v=20261018r',
      globalName: 'IndooneAddAccountManual'
    },
    import: {
      script:
        'lobby/accounts/add-account/import-uri/script.js?v=20261018j',
      globalName: 'IndooneAddAccountImport'
    }
  };

  const featurePromises = {};
  let menuMarkup = null;

  function loadStyle() {
    const baseUrl = MENU_STYLE_URL.split('?')[0];

    if (document.querySelector(`link[href^="${baseUrl}"]`)) {
      return;
    }

    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = MENU_STYLE_URL;
    document.head.appendChild(link);
  }

  async function loadMenuMarkup() {
    if (menuMarkup !== null) {
      return menuMarkup;
    }

    const response = await fetch(MENU_MARKUP_URL, {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Add Account menu could not be loaded.');
    }

    menuMarkup = await response.text();
    return menuMarkup;
  }

  function loadFeature(name) {
    const feature = FEATURES[name];

    if (!feature) {
      return Promise.reject(
        new Error(`Unknown Add Account feature: ${name}`)
      );
    }

    if (window[feature.globalName]?.render) {
      return Promise.resolve(window[feature.globalName]);
    }

    if (featurePromises[name]) {
      return featurePromises[name];
    }

    featurePromises[name] = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = feature.script;

      script.onload = () => {
        const loaded = window[feature.globalName];

        if (!loaded?.render) {
          reject(
            new Error(`${name} feature did not initialize.`)
          );
          return;
        }

        resolve(loaded);
      };

      script.onerror = () => {
        reject(
          new Error(`${name} feature could not be loaded.`)
        );
      };

      document.head.appendChild(script);
    }).catch(error => {
      delete featurePromises[name];
      throw error;
    });

    return featurePromises[name];
  }

  function baseHash() {
    return '#lobby/accounts/add-account';
  }

  function featureHash(name) {
    return `${baseHash()}/${name}`;
  }

  function pushHash(hash, stateName) {
    if (window.location.hash === hash) {
      return;
    }

    history.pushState(
      {
        indoonePage: stateName
      },
      '',
      hash
    );
  }

  function backToHome() {
    if (window.location.hash.startsWith(baseHash())) {
      history.back();
      return;
    }

    window.IndooneHome?.backToHome();
  }

  function goHome() {
    window.IndooneAddAccountQr?.stop?.();
    history.replaceState(
      {},
      '',
      window.location.pathname + window.location.search
    );
    window.showHome?.();
  }

  function bindMenu(root) {
    root
      .querySelectorAll('[data-add-action]')
      .forEach(button => {
        button.addEventListener('click', event => {
          event.preventDefault();

          const action = button.dataset.addAction;

          if (action === 'back') {
            backToHome();
            return;
          }

          if (FEATURES[action]) {
            void showFeature(action, {
              push: true
            });
          }
        });
      });
  }

  async function showMenu({ push = false } = {}) {
    const mount = document.getElementById('homeSubPage');

    if (!mount) {
      return;
    }

    window.IndooneAddAccountQr?.stop?.();

    if (push) {
      pushHash(baseHash(), 'add-account');
    }

    loadStyle();

    try {
      mount.innerHTML = await loadMenuMarkup();
      bindMenu(mount);
    } catch (error) {
      mount.innerHTML = `
        <section class="add-account-page">
          <button
            type="button"
            class="add-account-back"
            data-add-action="back"
          >
            <span class="back-icon">‹</span>
            <span>Back</span>
          </button>
          <h1>Add Account</h1>
          <p>Unable to load this page. Please try again.</p>
        </section>
      `;
      bindMenu(mount);
      console.error(
        'Sankalp Add Account menu failed:',
        error
      );
    }
  }

  async function showFeature(name, options = {}) {
    const mount = document.getElementById('homeSubPage');

    if (!mount) {
      return;
    }

    if (options.push !== false) {
      pushHash(featureHash(name), name);
    }

    if (name !== 'qr') {
      window.IndooneAddAccountQr?.stop?.();
    }

    try {
      const feature = await loadFeature(name);
      await feature.render(
        mount,
        options.renderOptions || {}
      );
    } catch (error) {
      mount.innerHTML = `
        <section class="add-account-page">
          <button
            type="button"
            class="add-account-back"
            data-add-action="back"
          >
            <span class="back-icon">‹</span>
            <span>Back</span>
          </button>
          <h1>Add Account</h1>
          <p>Unable to load this feature.</p>
        </section>
      `;

      mount
        .querySelector('[data-add-action="back"]')
        ?.addEventListener(
          'click',
          backToHome,
          {
            once: true
          }
        );

      console.error(
        `Sankalp Add Account ${name} feature failed:`,
        error
      );
    }
  }

  function handleHistory() {
    const hash = window.location.hash;
    const prefix = `${baseHash()}/`;

    if (hash === baseHash()) {
      void showMenu({
        push: false
      });
      return;
    }

    if (!hash.startsWith(prefix)) {
      return;
    }

    const featureName = hash
      .slice(prefix.length)
      .split('/')[0];

    if (FEATURES[featureName]) {
      void showFeature(featureName, {
        push: false
      });
    } else {
      void showMenu({
        push: false
      });
    }
  }

  async function render(mount) {
    if (!mount) {
      return;
    }

    mount.id = 'homeSubPage';
    mount.classList.add('home-sub-page');
    mount.hidden = false;

    if (window.location.hash === baseHash()) {
      await showMenu({
        push: false
      });
      return;
    }

    const prefix = `${baseHash()}/`;

    if (window.location.hash.startsWith(prefix)) {
      handleHistory();
      return;
    }

    await showMenu({
      push: false
    });
  }

  window.IndooneAddAccount = {
    render,
    showMenu,
    showFeature,
    showManual(options = {}) {
      return showFeature('manual', {
        push: options.push !== false,
        renderOptions: {
          id: options.id || 0,
          prefill: options.prefill || {}
        }
      });
    },
    backToHome,
    goHome,
    handleHistory
  };

  window.showAccountsPage = backToHome;

  window.showManual = function (prefill = {}) {
    return window.IndooneAddAccount.showManual({
      push: true,
      id: Number(prefill.id || 0),
      prefill
    });
  };

  window.saveAccount = function () {
    document
      .getElementById('manualAccountForm')
      ?.requestSubmit();
  };

  window.importOtpUri = function () {
    return window.IndooneAddAccount.showFeature(
      'import',
      {
        push: true
      }
    );
  };

  window.IndooneQrScanner = {
    start() {
      return window.IndooneAddAccount.showFeature(
        'qr',
        {
          push: true
        }
      );
    },

    stop() {
      window.IndooneAddAccountQr?.stop?.();
    }
  };

  window.addEventListener(
    'popstate',
    handleHistory
  );

  window.addEventListener(
    'hashchange',
    handleHistory
  );
})();
