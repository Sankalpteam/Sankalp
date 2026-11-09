// The web counterpart of android/menu/plugins/PluginsMenuScreen.kt.
//
// Three provider cards, not a flat list of Connect buttons: each one is a 52dp
// tinted tile with the provider's mark, its name and its state, and either a
// violet circle holding a + or a more menu offering Reauthorize and Disconnect.
// The paths and the shapes of the answers are the ones the app's GitHubPluginApi,
// ZohoMailPluginApi and GmailPluginApi call.
(() => {
  const BACKEND_URL = 'https://indooneai.duckdns.org';

  const PROVIDERS = [
    {
      id: 'email',
      title: 'Email',
      // There is no Gmail integration on the backend: app/ has no /integrations/gmail route at
      // all, so every call below answers 404. Offering the card anyway made the page say
      // "Connection status unavailable" for a provider that can never be connected, which reads
      // like a network blip rather than a missing feature.
      available: false,
      offNote: 'Connect your Gmail account',
      defaultAccount: 'Gmail account',
      status: 'api/integrations/gmail/status',
      // GmailPluginApi.getGoogleServerClientId reads auth-config with a GET -
      // it is not a connect endpoint, and posting to it is a 405. The rest of
      // the Gmail flow is Google Sign-In on the device, which then posts a
      // server auth code to gmail/server-auth-code.
      config: 'api/integrations/gmail/auth-config',
      disconnect: 'api/integrations/gmail/disconnect',
      // the app reads the address from a probe, not from the status answer:
      // GmailPluginApi.probeEmailAddress posts to gmail/probe and takes
      // summary.email_address
      probe: 'api/integrations/gmail/probe',
      addressPath: ['summary', 'email_address']
    },
    {
      id: 'zoho-mail',
      title: 'Zoho Mail',
      offNote: 'Connect your Zoho Mail account',
      defaultAccount: 'Zoho Mail account',
      status: 'api/integrations/zoho-mail/connection',
      connect: 'api/integrations/zoho-mail/start',
      disconnect: 'api/integrations/zoho-mail/disconnect',
      // the app lists the mailboxes through zoho-mail/accounts and takes the
      // first one's address - ZohoMailPluginApi.listAccounts
      accounts: 'api/integrations/zoho-mail/accounts',
      addressPath: ['data', 0, 'primaryEmailAddress']
    },
    {
      id: 'github',
      title: 'GitHub',
      offNote: 'Connect your GitHub account',
      defaultAccount: 'GitHub account',
      status: 'api/integrations/github/status',
      // GitHubPluginApi.startConnection posts user_id and redirect_uri, the
      // backend's own callback. user_id alone is a 422.
      connect: 'api/integrations/github/connect',
      callbackPath: '/api/integrations/github/callback',
      disconnect: 'api/integrations/github/disconnect',
      // the app probes for the login and the provider's health:
      // GitHubPluginApi.getConnectionStatus posts to github/probe and takes
      // summary.login and provider_ok
      probe: 'api/integrations/github/probe',
      addressPath: ['summary', 'login'],
      healthPath: ['provider_ok'],
      browse: true
    }
  ];

  // The backend rejects an empty user_id with a 422, so the id is read once
  // here and the page says plainly when there is no session rather than
  // sending nothing.
  function currentUser() {
    const user = window.IndooneFirebase?.auth?.currentUser;

    if (!user?.uid) {
      throw new Error('Please sign in again.');
    }

    return user;
  }

  function userId() {
    return currentUser().uid;
  }

  async function token() {
    return currentUser().getIdToken(false);
  }

  async function call(path, options = {}) {
    const idToken = await token();
    const body = options.body;
    const headers = {
      'Authorization': `Bearer ${idToken}`,
      ...(options.headers || {})
    };

    // A GET carries no body, so asking the browser to announce a JSON
    // content-type on it only adds a header to the cross-origin preflight for
    // no reason. It is sent when there is something to send.
    if (body) {
      headers['Content-Type'] = 'application/json';
    }

    const response = await fetch(`${BACKEND_URL}/${path}`, {
      ...options,
      headers
    });

    if (!response.ok) {
      throw new Error(`Sankalp backend returned ${response.status}`);
    }

    return response.json().catch(() => ({}));
  }

  async function statusOf(provider) {
    const id = encodeURIComponent(currentUser().uid);
    return call(`${provider.status}?user_id=${id}`);
  }

  window.IndoonePlugins = { PROVIDERS, call, statusOf, userId, baseUrl: BACKEND_URL };
})();

// The providers' own marks. GitHub and Zoho Mail are brand marks with their own
// colours, so they live with the screen that shows them rather than in the app's
// icon set. Both are traced from the app's drawables - github_logo.xml and
// zoho_mail_logo.xml - at the same viewBox the app uses.
window.IndoonePluginMarks = {
  email: '<svg viewBox="0 0 24 24" fill="none" stroke="#5E2DD2" stroke-width="1.7" ' +
    'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<rect x="3" y="5" width="18" height="14" rx="2.5"></rect>' +
    '<path d="m3.6 7 8.4 6 8.4-6"></path></svg>',

  github: '<svg viewBox="0 0 24 24" aria-hidden="true">' +
    '<path fill="#181717" d="M12 0.297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 ' +
    '8.205 11.385 0.6 0.111 0.82-0.261 0.82-0.577 0-0.285-0.01-1.04-0.015-2.04 ' +
    '-3.338 0.724-4.042-1.61-4.042-1.61-0.546-1.387-1.333-1.756-1.333-1.756 ' +
    '-1.09-0.745 0.083-0.729 0.083-0.729 1.205 0.084 1.84 1.237 1.84 1.237 ' +
    '1.07 1.834 2.807 1.304 3.492 0.997 0.108-0.775 0.418-1.305 0.762-1.605 ' +
    '-2.665-0.3-5.466-1.332-5.466-5.93 0-1.31 0.467-2.381 1.236-3.221 ' +
    '-0.124-0.303-0.536-1.523 0.117-3.176 0 0 1.008-0.322 3.301 1.23 ' +
    '0.957-0.266 1.983-0.399 3.005-0.404 1.02 0.005 2.045 0.138 3.005 0.404 ' +
    '2.291-1.552 3.297-1.23 3.297-1.23 0.655 1.653 0.243 2.873 0.12 3.176 ' +
    '0.77 0.84 1.235 1.911 1.235 3.221 0 4.61-2.805 5.625-5.475 5.921 ' +
    '0.43 0.372 0.823 1.102 0.823 2.222 0 1.606-0.015 2.898-0.015 3.293 ' +
    '0 0.319 0.216 0.694 0.825 0.576 4.801-1.585 8.236-6.085 8.236-11.38 ' +
    '0-6.627-5.373-12-12-12z"></path></svg>',

  'zoho-mail': '<svg viewBox="0 0 16 16" aria-hidden="true">' +
    '<path fill="#E4E4E4" d="M8.008 0C7.559 0 7.12 0.134 6.751 0.389L0.951 4.389' +
    'C0.356 4.8 0 5.477 0 6.2V12.8C0 12.8-0.03 13.49 0.34 14.257 0.711 15.024 ' +
    '1.925 16 3.167 16H12.833C14.075 16 15.289 15.024 15.66 14.257 ' +
    '16.03 13.49 16 12.8 16 12.8V6.2C16 5.477 15.644 4.8 15.049 4.389L9.249 0.389' +
    'C8.884 0.137 8.451 0 8.008 0Z"></path>' +
    '<path fill="none" stroke="#2F7ABB" stroke-width="0.95" stroke-linecap="round" ' +
    'stroke-linejoin="round" d="M2.5 10.5V12.5C2.5 12.5 2.5 13.5 3.466 13.5H12.534' +
    'C13.5 13.5 13.5 12.5 13.5 12.5V6.2L8 2.5L2.5 6.2V8.483L8 11.5L11.5 9.5"></path>' +
    '<path fill="none" stroke="#FFAD42" stroke-width="0.95" stroke-linecap="round" ' +
    'stroke-linejoin="round" d="M11.5 9.5V7.508C11.5 6.533 10.516 6.5 10.516 6.5' +
    'H5.484C5.484 6.5 4.5 6.767 4.5 7.5"></path></svg>'
};

window.initMenuPlugins = function () {
  const host = document.getElementById('menuPluginsProviders');
  const statusLine = document.getElementById('menuPluginsStatus');
  const githubLine = document.getElementById('menuPluginsGithubStatus');
  const checking = document.getElementById('menuPluginsChecking');
  const dialog = document.getElementById('menuPluginsDialog');
  const dialogTitle = document.getElementById('menuPluginsDialogTitle');
  const dialogConfirm = document.getElementById('menuPluginsDialogConfirm');
  const dialogCancel = document.getElementById('menuPluginsDialogCancel');

  // one piece of state per provider, exactly as the app keeps it
  const state = {};
  window.IndoonePlugins.PROVIDERS.forEach(provider => {
    state[provider.id] = { connected: null, account: '', healthy: false };
  });

  let pending = null;
  let busyProvider = null;
  let statusMessage = '';
  // the app shows "Checking connections…" only while the first look is running,
  // and takes it away when the answers are in
  let loading = true;

  function setStatus(text) {
    statusMessage = text || '';
    if (statusLine) statusLine.textContent = statusMessage;
  }

  function connectedColour() {
    return '#218653';
  }

  function mark(provider) {
    return window.IndoonePluginMarks?.[provider.id] || '';
  }

  function moreMenu(provider) {
    const wrap = document.createElement('div');
    wrap.className = 'plugins-more';

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'plugins-more-btn';
    button.setAttribute('aria-label', `More options for ${provider.title}`);
    button.innerHTML = '<span data-app-icon="moreVert"></span>';

    const menu = document.createElement('div');
    menu.className = 'plugins-menu';
    menu.hidden = true;

    const reauthorize = document.createElement('button');
    reauthorize.type = 'button';
    reauthorize.innerHTML = '<span data-app-icon="refresh"></span><span>Reauthorize</span>';
    reauthorize.addEventListener('click', () => {
      menu.hidden = true;
      void startAuthorization(provider);
    });

    const disconnect = document.createElement('button');
    disconnect.type = 'button';
    disconnect.innerHTML = '<span data-app-icon="linkOff"></span><span>Disconnect</span>';
    disconnect.addEventListener('click', () => {
      menu.hidden = true;
      askDisconnect(provider);
    });

    menu.appendChild(reauthorize);
    menu.appendChild(disconnect);

    button.addEventListener('click', event => {
      event.stopPropagation();
      menu.hidden = !menu.hidden;
    });

    document.addEventListener('click', () => {
      menu.hidden = true;
    });

    wrap.appendChild(button);
    wrap.appendChild(menu);
    return wrap;
  }

  function plusButton(provider) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'plugins-add';
    button.setAttribute('aria-label', `Connect ${provider.title}`);

    if (busyProvider === provider.id) {
      button.disabled = true;
      button.classList.add('busy');
      button.innerHTML = '<span class="plugins-spinner"></span>';
    } else {
      button.innerHTML = '<span data-app-icon="add"></span>';
      button.addEventListener('click', () => void startAuthorization(provider));
    }

    return button;
  }

  function row(provider) {
    const current = state[provider.id];

    const card = document.createElement('div');
    card.className = 'plugins-card';

    const tile = document.createElement('span');
    tile.className = 'plugins-tile';
    tile.innerHTML = mark(provider);

    const text = document.createElement('span');
    text.className = 'plugins-text';

    const title = document.createElement('b');
    title.textContent = provider.title;

    const note = document.createElement('small');
    const line = document.createElement('i');

    if (current.connected === true) {
      note.textContent = current.account || provider.defaultAccount;

      if (provider.id === 'github' && !current.healthy) {
        line.textContent = '● Reauthorization needed';
        line.style.color = '#B45309';
      } else {
        line.textContent = '● Connected';
        line.style.color = connectedColour();
      }
    } else if (provider.available === false) {
      note.textContent = 'Not available yet';
    } else if (current.connected === false) {
      note.textContent = provider.offNote;
    } else {
      note.textContent = current.reason
        ? `Connection status unavailable · ${current.reason}`
        : 'Connection status unavailable';
    }

    text.appendChild(title);
    text.appendChild(note);
    if (line.textContent) text.appendChild(line);

    card.appendChild(tile);
    card.appendChild(text);

    // GitHub keeps a way into its own screen whether or not it is connected
    if (provider.browse) {
      const browse = document.createElement('button');
      browse.type = 'button';
      browse.className = 'plugins-browse';
      browse.textContent = 'Browse';
      browse.addEventListener('click', () => window.openMenuNested?.('plugins/github'));
      card.appendChild(browse);
    }

    if (provider.available === false) {
      // no + and no more menu: there is nothing behind either of them
    } else if (current.connected === true) {
      card.appendChild(moreMenu(provider));
    } else {
      card.appendChild(plusButton(provider));
    }

    return card;
  }

  function draw() {
    if (!host) return;
    host.textContent = '';
    window.IndoonePlugins.PROVIDERS.forEach(provider => host.appendChild(row(provider)));

    if (githubLine) {
      const github = state.github;
      const text = github.connected === null
        ? 'Could not check GitHub connection. You can retry with +.'
        : github.connected === false
          ? 'GitHub is not connected yet.'
          : github.healthy
            ? `GitHub connected and verified${github.account ? ` as ${github.account}` : ''}.`
            : 'GitHub is connected, but API verification failed. Reauthorize to restore access.';

      githubLine.textContent = text;
      githubLine.hidden = false;
      githubLine.style.color = github.connected === true && github.healthy
        ? connectedColour()
        : github.connected === true
          ? '#B45309'
          : '#777286';
    }

    if (checking) checking.hidden = !loading;

    // the icons are drawn after the markup is in the page
    window.IndooneIcons?.redraw?.();
  }

  // read a value out of an answer by the path the app reads it by, so a nested
  // summary.login or a first element of data[] is written the same way here
  function at(answer, path) {
    if (!path) return '';

    return path.reduce((value, step) => {
      if (value === null || value === undefined) return undefined;
      return value[step];
    }, answer);
  }

  // "Sankalp backend returned 401" becomes "401"; a thrown TypeError becomes "network".
  function shortReason(error) {
    const message = String(error?.message || '');

    const status = message.match(/\b(\d{3})\b/);
    if (status) return status[1];

    if (/fetch|network|load failed/i.test(message)) return 'network';

    return message.slice(0, 24) || 'failed';
  }

  async function refresh() {
    loading = true;
    if (checking) checking.hidden = false;

    let firstProblem = '';

    await Promise.all(window.IndoonePlugins.PROVIDERS.map(async provider => {
      const entry = state[provider.id];

      if (provider.available === false) {
        entry.connected = false;
        entry.account = '';
        return;
      }

      try {
        const answer = await window.IndoonePlugins.statusOf(provider);
        entry.connected = Boolean(answer?.connected || answer?.authorized);
        entry.healthy = true;
        entry.account = '';

        if (!entry.connected) {
          return;
        }

        // The app asks a second question once it knows the provider is
        // connected: what is the account called, and is it still healthy.
        // The status answer carries neither.
        if (provider.probe) {
          const probe = await window.IndoonePlugins.call(provider.probe, {
            method: 'POST',
            body: JSON.stringify({ user_id: window.IndoonePlugins.userId() })
          });

          entry.account = String(at(probe, provider.addressPath) || '');
          entry.healthy = at(probe, provider.healthPath) !== false;
        } else if (provider.accounts) {
          const list = await window.IndoonePlugins.call(provider.accounts, {
            method: 'POST',
            body: JSON.stringify({ user_id: window.IndoonePlugins.userId() })
          });

          entry.account = String(at(list, provider.addressPath) || '');
        }
      } catch (error) {
        // Keep the reason. "Connection status unavailable" told a person nothing and told me
        // nothing either: a 404 (no such route), a 401 (token refused) and a network failure all
        // looked the same. The card now carries the short reason.
        entry.connected = null;
        entry.reason = shortReason(error);
        if (!firstProblem) firstProblem = error?.message || 'Could not reach the backend.';
      }
    }));

    if (firstProblem && !statusMessage) setStatus(firstProblem);

    loading = false;
    draw();
  }

  function askDisconnect(provider) {
    pending = provider;
    if (dialogTitle) dialogTitle.textContent = `Disconnect ${provider.title}?`;
    if (dialog) dialog.hidden = false;
  }

  function closeDialog() {
    pending = null;
    if (dialog) dialog.hidden = true;
  }

  async function disconnect(provider) {
    busyProvider = provider.id;
    draw();

    try {
      await window.IndoonePlugins.call(provider.disconnect, { method: 'POST', body: JSON.stringify({}) });
      state[provider.id].connected = false;
      state[provider.id].account = '';
      state[provider.id].healthy = false;
      setStatus(`${provider.title} disconnected.`);
    } catch (error) {
      setStatus(error?.message || `Could not disconnect ${provider.title}. Please try again.`);
    } finally {
      busyProvider = null;
      closeDialog();
      draw();
    }
  }

  async function startAuthorization(provider) {
    if (busyProvider) return;
    busyProvider = provider.id;
    setStatus('');
    draw();

    try {
      if (provider.config) {
        // Gmail: the app reads the Google client id with a GET and then signs
        // in with Google on the device, which a browser page cannot do.
        const config = await window.IndoonePlugins.call(provider.config, { method: 'GET' });
        const clientId = String(config?.server_client_id || '');

        setStatus(clientId
          ? 'Gmail connects through Google Sign-In, which this page cannot open. Connect it in the Sankalp app.'
          : 'Google sign-in is not configured on the Sankalp backend.');
        return;
      }

      const body = { user_id: window.IndoonePlugins.userId() };

      // GitHubPluginApi.startConnection sends the backend's own callback too,
      // and the route refuses the call without it
      if (provider.callbackPath) {
        body.redirect_uri = `${window.IndoonePlugins.baseUrl}${provider.callbackPath}`;
      }

      const answer = await window.IndoonePlugins.call(provider.connect, {
        method: 'POST',
        body: JSON.stringify(body)
      });
      const url = answer?.authorization_url || answer?.url || answer?.authorizationUrl;

      if (url) {
        window.open(url, '_blank', 'noopener');
        setStatus(`Approve Sankalp in ${provider.title}. Waiting for connection verification…`);
      } else {
        setStatus(`${provider.title} connection started.`);
      }
    } catch (error) {
      setStatus(error?.message || `Could not start ${provider.title} authorization. Please retry.`);
    } finally {
      busyProvider = null;
      draw();
    }
  }

  dialogCancel?.addEventListener('click', closeDialog);
  dialogConfirm?.addEventListener('click', () => {
    if (pending) void disconnect(pending);
  });

  void refresh();
};
