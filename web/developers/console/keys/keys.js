// Developer keys: authenticated server state only. Never store secret keys in
// browser storage, build artifacts, logs, or the navigation URL.
(function () {
  'use strict';

  const $ = id => document.getElementById(id);
  const form = $('dkForm');
  const table = $('dkRows');
  const create = $('dkCreate');
  const notice = $('dkStatus');
  const secretSection = $('dkOneTime');
  const secretCode = $('dkSecret');
  let uid = '';
  let lastCreatedSecret = '';

  function say(message) {
    notice.textContent = String(message || '');
  }

  function hideSecret() {
    lastCreatedSecret = '';
    secretCode.textContent = '';
    secretSection.hidden = true;
  }

  function account() {
    const current = window.IndooneFirebase?.auth?.currentUser;
    if (!current || current.uid !== uid) {
      throw new Error('Sign in to Sankalp to manage your developer keys.');
    }
    return current;
  }

  async function api(path, options = {}) {
    const user = account();
    const originalUid = user.uid;
    const idToken = await user.getIdToken(false);
    if (window.IndooneFirebase?.auth?.currentUser?.uid !== originalUid) {
      throw new Error('Account changed. Sign in again.');
    }
    const backend = String(window.SANKALP_BACKEND || '').replace(/\/+$/, '');
    if (!/^https:\/\//.test(backend) && !/^http:\/\/localhost(?::\d+)?$/.test(backend)) {
      throw new Error('Backend URL is not configured securely.');
    }
    const response = await fetch(backend + '/api/developers/' + path, {
      ...options,
      headers: {
        Authorization: 'Bearer ' + idToken,
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      },
      cache: 'no-store',
    });
    if (window.IndooneFirebase?.auth?.currentUser?.uid !== originalUid) {
      throw new Error('Account changed; response discarded.');
    }
    const json = await response.json().catch(() => ({}));
    if (!response.ok) {
      const detail = typeof json.detail === 'string' ? json.detail : 'Request failed';
      throw new Error(response.status + ': ' + detail);
    }
    return json;
  }

  function cell(row, value, className) {
    const td = document.createElement('td');
    td.textContent = String(value ?? '');
    if (className) td.className = className;
    row.appendChild(td);
    return td;
  }

  function date(value) {
    if (!value) return '—';
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString();
  }

  async function loadKeys() {
    const data = await api('keys');
    table.replaceChildren();
    const records = Array.isArray(data.keys) ? data.keys : [];
    if (!records.length) {
      const row = document.createElement('tr');
      const empty = cell(row, 'No API keys created for this account.');
      empty.colSpan = 6;
      table.appendChild(row);
      return;
    }
    records.forEach(record => {
      const row = document.createElement('tr');
      cell(row, record.name || 'Untitled');
      cell(row, record.display || '', 'mono');
      cell(row, record.environment || '');
      cell(row, record.status || '');
      cell(row, date(record.created_at), 'mini');
      const actions = document.createElement('td');
      if (record.status === 'active') {
        const revoke = document.createElement('button');
        revoke.type = 'button';
        revoke.className = 'btn btn-sm';
        revoke.textContent = 'Revoke';
        revoke.addEventListener('click', async () => {
          if (!window.confirm('Revoke this key? Requests using it will be refused when key authentication is enabled.')) return;
          revoke.disabled = true;
          try {
            await api('keys/' + encodeURIComponent(record.id), { method: 'DELETE' });
            hideSecret();
            await loadKeys();
            say('Key revoked.');
          } catch (error) {
            say(error.message);
            revoke.disabled = false;
          }
        });
        actions.appendChild(revoke);
      } else {
        actions.textContent = '—';
      }
      row.appendChild(actions);
      table.appendChild(row);
    });
  }

  async function refresh() {
    create.disabled = true;
    hideSecret();
    try {
      const status = await api('status');
      if (!status.key_management_enabled) {
        table.replaceChildren();
        say('Developer key management is disabled on this backend. Paid model access and wallet billing are not live.');
        return;
      }
      await loadKeys();
      create.disabled = false;
      say('Keys are managed by the authenticated backend. Paid inference and charging are not yet enabled.');
    } catch (error) {
      table.replaceChildren();
      say(error.message || 'Could not reach developer key management.');
    }
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (create.disabled) return;
    const name = $('dkName').value.trim();
    const environment = $('dkEnvironment').value;
    if (!name) {
      say('Enter a name for the key.');
      return;
    }
    create.disabled = true;
    hideSecret();
    try {
      const result = await api('keys', {
        method: 'POST',
        body: JSON.stringify({ name, environment }),
      });
      lastCreatedSecret = String(result.key || '');
      if (!lastCreatedSecret) throw new Error('Key creation returned no secret. Do not retry automatically.');
      secretCode.textContent = lastCreatedSecret;
      secretSection.hidden = false;
      $('dkName').value = '';
      await loadKeys();
      say('Key created. Copy the secret now; it will not be shown again. No paid model access is enabled.');
    } catch (error) {
      say(error.message || 'Could not create the key.');
    } finally {
      create.disabled = false;
    }
  });

  $('dkCopy').addEventListener('click', async () => {
    if (!lastCreatedSecret) return;
    try {
      await navigator.clipboard.writeText(lastCreatedSecret);
      say('Key copied. Store it in a server-side secret manager, not in browser code.');
    } catch (_) {
      say('Clipboard unavailable. Select and copy the visible key manually.');
    }
  });
  $('dkDone').addEventListener('click', hideSecret);

  const auth = window.IndooneFirebase?.auth;
  if (!auth) {
    say('Firebase sign-in is unavailable. Return to the Sankalp app and sign in.');
    return;
  }
  Promise.resolve(window.IndooneFirebase.persistenceReady).then(() => {
    auth.onAuthStateChanged(user => {
      const changed = uid !== (user?.uid || '');
      uid = user?.uid || '';
      if (changed) {
        table.replaceChildren();
        hideSecret();
      }
      if (!uid) {
        create.disabled = true;
        say('Sign in to Sankalp to manage developer keys.');
        return;
      }
      void refresh();
    }, () => {
      create.disabled = true;
      hideSecret();
      say('Could not verify your sign-in.');
    });
  });
})();
