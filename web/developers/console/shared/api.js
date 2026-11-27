/* Sankalp console — the one place the console reads data.
 *
 * The console reads Realtime Database and never writes it. A balance a browser can write is
 * a balance that is already wrong, so the rules deny every client write under console/ and
 * the backend is the only writer.
 *
 * Every screen uses this file instead of talking to firebase.database() directly: one place
 * to change a path, one place to handle "not signed in", one place to decide what an empty
 * console looks like.
 *
 * Requires, in this order, before this file:
 *   firebase-app-compat.js, firebase-auth-compat.js, firebase-database-compat.js
 *   firebase-config.js   (initialises firebase.app(), firebase.auth(), firebase.database())
 */
(function () {
  'use strict';

  const EMPTY = Object.freeze({
    keys: {},
    usage: {},
    wallet: { balancePaise: 0, updatedAt: 0, ledger: {} },
  });

  function db() {
    if (!window.firebase || !firebase.database) return null;
    try {
      return firebase.database();
    } catch (e) {
      return null;
    }
  }

  function currentUser() {
    if (!window.firebase || !firebase.auth) return null;
    return firebase.auth().currentUser || null;
  }

  /** The signed-in uid, or '' — never throws, because a page must render when signed out. */
  function uid() {
    const user = currentUser();
    return user ? String(user.uid || '') : '';
  }

  function root() {
    const id = uid();
    if (!id) return null;
    const handle = db();
    return handle ? handle.ref(`console/${id}`) : null;
  }

  /** Resolve once with the whole console subtree, or EMPTY when signed out. */
  function readAll() {
    const ref = root();
    if (!ref) return Promise.resolve(EMPTY);
    return ref
      .once('value')
      .then((snap) => normalise(snap.val()))
      .catch(() => EMPTY);
  }

  /**
   * Live updates. `onData` is called now and on every change.
   * Returns an unsubscribe function — call it when the page goes away, or a signed-out
   * user's listener keeps the socket open.
   */
  function watchAll(onData, onError) {
    const ref = root();
    if (!ref) {
      onData(EMPTY);
      return function unsubscribe() {};
    }
    const handler = (snap) => onData(normalise(snap.val()));
    const errorHandler = (err) => {
      if (typeof onError === 'function') onError(err);
    };
    ref.on('value', handler, errorHandler);
    return function unsubscribe() {
      ref.off('value', handler);
    };
  }

  /** Fill in what a half-written record is missing, so a screen never renders undefined. */
  function normalise(raw) {
    const value = raw && typeof raw === 'object' ? raw : {};
    const wallet = value.wallet && typeof value.wallet === 'object' ? value.wallet : {};
    return {
      keys: value.keys && typeof value.keys === 'object' ? value.keys : {},
      usage: value.usage && typeof value.usage === 'object' ? value.usage : {},
      wallet: {
        balancePaise: Number(wallet.balancePaise) || 0,
        updatedAt: Number(wallet.updatedAt) || 0,
        ledger: wallet.ledger && typeof wallet.ledger === 'object' ? wallet.ledger : {},
      },
    };
  }

  /** Keys as an array, newest first, with the id kept beside the record. */
  function keyList(data) {
    const keys = (data || EMPTY).keys || {};
    return Object.keys(keys)
      .map((id) => Object.assign({ id }, keys[id]))
      .sort((a, b) => (Number(b.createdAt) || 0) - (Number(a.createdAt) || 0));
  }

  /** The usage node for one month key ("2026-11"), or zeroes. */
  function usageFor(data, monthKey) {
    const usage = (data || EMPTY).usage || {};
    const month = usage[monthKey] || {};
    return {
      requests: Number(month.requests) || 0,
      inputTokens: Number(month.inputTokens) || 0,
      outputTokens: Number(month.outputTokens) || 0,
      spentPaise: Number(month.spentPaise) || 0,
      byAlias: month.byAlias && typeof month.byAlias === 'object' ? month.byAlias : {},
    };
  }

  /** Ledger entries as an array, newest first. */
  function ledgerList(data) {
    const ledger = ((data || EMPTY).wallet || {}).ledger || {};
    return Object.keys(ledger)
      .map((id) => Object.assign({ id }, ledger[id]))
      .sort((a, b) => (Number(b.at) || 0) - (Number(a.at) || 0));
  }

  /** The backend that writes this data. Empty until the gateway is deployed. */
  const BACKEND = window.SANKALP_CONSOLE_BACKEND || '';

  /**
   * A write. There is exactly one of these on purpose: every write goes to the backend,
   * with the user's Firebase token, and the backend writes RTDB with admin credentials.
   */
  function post(path, body) {
    if (!BACKEND) {
      return Promise.reject(
        new Error('The API is not deployed yet. Keys will be creatable once the gateway is live.')
      );
    }
    const user = currentUser();
    if (!user) return Promise.reject(new Error('Sign in to continue.'));
    return user.getIdToken().then((token) =>
      fetch(`${BACKEND}${path}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body || {}),
      }).then(async (response) => {
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
          throw new Error(payload.detail || payload.error || `Request failed (${response.status})`);
        }
        return payload;
      })
    );
  }

  /** Render a screen only once we know who is signed in. */
  function onSignedIn(callback) {
    if (!window.firebase || !firebase.auth) return;
    firebase.auth().onAuthStateChanged((user) => callback(user || null));
  }

  window.SankalpConsole = {
    uid,
    readAll,
    watchAll,
    keyList,
    usageFor,
    ledgerList,
    post,
    onSignedIn,
    BACKEND,
    EMPTY,
  };
})();
