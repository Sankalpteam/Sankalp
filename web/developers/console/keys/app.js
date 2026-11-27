/* API keys screen.
 *
 * Reads console/<uid>/keys and draws the table the page already has. Writes nothing to
 * Realtime Database: creating and revoking go through the gateway, which is the only
 * writer. Until that is deployed, the buttons say so instead of pretending.
 *
 * Requires, in order: firebase compat SDK, firebase-config.js, ../shared/format.js,
 * ../shared/api.js.
 */
(function () {
  'use strict';

  const F = window.SankalpFormat;
  const API = window.SankalpConsole;
  const tbody = document.querySelector('table tbody');
  const createButton = document.querySelector('.mainhead .btn.primary');
  if (!tbody || !API) return;

  let unsubscribe = null;
  let latest = API.EMPTY;

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }

  /** The key as a person should see it: the prefix we store, then dots, never the secret. */
  function maskedKey(record) {
    const prefix = String(record.prefix || 'sk_sankalp_');
    return `${prefix}${'\u2022'.repeat(4)}`;
  }

  function row(record) {
    const revoked = Boolean(record.revokedAt);
    const envClass = record.env === 'test' ? 'warn' : 'ok';
    const envLabel = escapeHtml(record.env || 'live');
    const actions = revoked
      ? '<span class="mini">revoked</span>'
      : `<button class="btn btn-sm" type="button" data-copy="${escapeHtml(record.prefix || '')}">Copy</button>
         <button class="btn btn-sm" type="button" data-revoke="${escapeHtml(record.id)}">Revoke</button>`;

    return `<tr${revoked ? ' class="muted"' : ''}>
      <td><b>${escapeHtml(record.name || 'Untitled key')}</b></td>
      <td><span class="key mono">${escapeHtml(maskedKey(record))}</span></td>
      <td><span class="pill ${envClass}">${envLabel}</span></td>
      <td class="mini">${escapeHtml(F.formatDay(record.createdAt))}</td>
      <td class="mini">${revoked ? '—' : escapeHtml(F.formatWhen(record.lastUsedAt))}</td>
      <td class="right mini">${escapeHtml(F.formatCount(record.requests))}</td>
      <td class="right">${actions}</td>
    </tr>`;
  }

  function empty() {
    return `<tr><td colspan="7" class="mini">
      No keys yet. Create one and it appears here — the key itself is shown once, at creation.
    </td></tr>`;
  }

  function render(data) {
    latest = data;
    const keys = API.keyList(data);
    tbody.innerHTML = keys.length ? keys.map(row).join('') : empty();
  }

  function say(message, kind) {
    const note = document.getElementById('keys-note');
    if (!note) {
      if (message) console.info(message);
      return;
    }
    note.textContent = message || '';
    note.className = `note ${kind || ''}`.trim();
  }

  /** Copy the prefix — the whole key does not exist here, by design. */
  function copyPrefix(prefix) {
    if (!prefix || !navigator.clipboard) {
      say('The full key is shown once, when it is created. This row keeps only its prefix.', 'warn');
      return;
    }
    navigator.clipboard.writeText(prefix).then(
      () => say(`Copied ${prefix}… — the rest of the key is not stored anywhere readable.`, 'ok'),
      () => say('Could not copy. The key is shown once, at creation.', 'warn')
    );
  }

  function revoke(keyId) {
    const record = (latest.keys || {})[keyId];
    const name = record ? record.name : 'this key';
    if (!window.confirm(`Revoke "${name}"? Requests using it start failing immediately.`)) return;
    say('Revoking…');
    API.post('/v1/keys/revoke', { key_id: keyId }).then(
      () => say('Revoked. The list updates itself.', 'ok'),
      (error) => say(error.message, 'warn')
    );
  }

  function create() {
    const name = window.prompt('Name this key (for example: Production server)');
    if (name === null) return;
    say('Creating…');
    API.post('/v1/keys', { name: String(name).slice(0, 60) }).then(
      (created) => {
        say('Created. Copy it now — it is not shown again.', 'ok');
        if (created && created.key) window.prompt('Your new API key (copy it now):', created.key);
      },
      (error) => say(error.message, 'warn')
    );
  }

  tbody.addEventListener('click', (event) => {
    const copyTarget = event.target.closest('[data-copy]');
    if (copyTarget) return copyPrefix(copyTarget.getAttribute('data-copy'));
    const revokeTarget = event.target.closest('[data-revoke]');
    if (revokeTarget) return revoke(revokeTarget.getAttribute('data-revoke'));
  });

  if (createButton) createButton.addEventListener('click', create);

  API.onSignedIn((user) => {
    if (unsubscribe) {
      unsubscribe();
      unsubscribe = null;
    }
    if (!user) {
      render(API.EMPTY);
      say('Sign in to see your keys.', 'warn');
      return;
    }
    say('');
    unsubscribe = API.watchAll(render, (error) => say(`Could not read keys: ${error.message}`, 'warn'));
  });

  window.addEventListener('pagehide', () => {
    if (unsubscribe) unsubscribe();
  });
})();
