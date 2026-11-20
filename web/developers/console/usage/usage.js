// Developer usage overview: counts only from the backend's deduplicated,
// provider-verified ledger. No local estimates, invented prices or wallet math.
(function () {
  'use strict';
  const status = document.getElementById('duStatus');
  const requests = document.getElementById('duRequests');
  const input = document.getElementById('duInput');
  const output = document.getElementById('duOutput');
  const billing = document.getElementById('duBilling');
  const auth = window.IndooneFirebase?.auth;
  let userId = '';

  function blank() {
    requests.textContent = '—';
    input.textContent = '—';
    output.textContent = '—';
    billing.textContent = 'Not enabled';
  }

  async function load(user) {
    if (!user || user.uid !== userId) return;
    const token = await user.getIdToken(false);
    if (auth.currentUser?.uid !== user.uid) return;
    const backend = String(window.SANKALP_BACKEND || '').replace(/\/+$/, '');
    if (!/^https:\/\//.test(backend) && !/^http:\/\/localhost(?::\d+)?$/.test(backend)) {
      throw new Error('Backend URL is not configured securely.');
    }
    const response = await fetch(backend + '/api/developers/usage', {
      headers: { Authorization: 'Bearer ' + token },
      cache: 'no-store',
    });
    if (auth.currentUser?.uid !== user.uid) return;
    if (!response.ok) {
      throw new Error(response.status === 503
        ? 'Developer usage ledger is disabled on this backend.'
        : 'Could not load your authenticated usage.');
    }
    const data = await response.json();
    if (auth.currentUser?.uid !== user.uid) return;
    const number = value => Number.isSafeInteger(value) && value >= 0
      ? value.toLocaleString()
      : '—';
    requests.textContent = number(data.requests);
    input.textContent = number(data.input_tokens);
    output.textContent = number(data.output_tokens);
    billing.textContent = data.billing_enabled ? 'Pending audit' : 'Not enabled';
    status.textContent = 'Verified provider usage only. No token estimates or payment charges.';
  }

  blank();
  if (!auth) {
    status.textContent = 'Firebase sign-in is unavailable. Return to Sankalp and sign in.';
    return;
  }
  Promise.resolve(window.IndooneFirebase.persistenceReady).then(() => {
    auth.onAuthStateChanged(user => {
      userId = user?.uid || '';
      blank();
      if (!userId) {
        status.textContent = 'Sign in to Sankalp to see your own developer usage.';
        return;
      }
      status.textContent = 'Loading your verified usage…';
      void load(user).catch(error => {
        blank();
        status.textContent = error.message;
      });
    }, () => {
      blank();
      status.textContent = 'Could not verify account identity.';
    });
  });
})();
