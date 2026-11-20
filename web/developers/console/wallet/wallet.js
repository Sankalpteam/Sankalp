// Owner-bound READ-ONLY developer wallet preparedness status.
// Do not expose payment actions or allow storage-backed identity spoofing.
(function () {
  'use strict';

  const $ = id => document.getElementById(id);
  const auth = window.IndooneFirebase?.auth;
  let currentUid = '';

  function reset() {
    $('dwAvailable').textContent = '—';
    $('dwAvailableHint').textContent = 'Paid API access and wallet funding are disabled.';
    $('dwHeld').textContent = '—';
    $('dwPending').textContent = '—';
  }

  function money(paise) {
    if (!Number.isSafeInteger(paise) || paise < 0) {
      throw new Error('Invalid backend wallet balance.');
    }
    return '₹' + (paise / 100).toLocaleString('en-IN', {
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    });
  }

  async function refresh(user) {
    if (!user || user.uid !== currentUid) return;
    const token = await user.getIdToken(false);
    if (auth.currentUser?.uid !== user.uid) return;
    const backend = String(window.SANKALP_BACKEND || '').replace(/\/+$/, '');
    if (!/^https:\/\//.test(backend) && !/^http:\/\/localhost(?::\d+)?$/.test(backend)) {
      throw new Error('Backend URL is not configured securely.');
    }

    const response = await fetch(backend + '/api/developers/wallet', {
      headers: { Authorization: 'Bearer ' + token },
      cache: 'no-store',
    });
    if (auth.currentUser?.uid !== user.uid) return;
    if (response.status === 503) {
      throw new Error('Developer wallet is disabled on this backend.');
    }
    if (!response.ok) {
      throw new Error('Could not load the authenticated developer wallet.');
    }
    const data = await response.json();
    if (auth.currentUser?.uid !== user.uid) return;
    if (data.currency !== 'INR' || data.payments_enabled !== false ||
        data.paid_inference_enabled !== false ||
        !Number.isSafeInteger(data.pending_requests) || data.pending_requests < 0) {
      throw new Error('Unexpected wallet response; no figures displayed.');
    }
    const available = money(data.available_paise);
    const held = money(data.held_paise);
    $('dwAvailable').textContent = available;
    $('dwHeld').textContent = held;
    $('dwPending').textContent = data.pending_requests.toLocaleString('en-IN');
    $('dwAvailableHint').textContent = 'Read-only internal reservation ledger; not a spendable payment balance.';
    $('dwStatus').textContent = 'Authenticated backend values only. Top-ups and paid API requests are not enabled.';
  }

  reset();
  if (!auth) {
    $('dwStatus').textContent = 'Firebase sign-in unavailable. Return to Sankalp and sign in.';
    return;
  }
  Promise.resolve(window.IndooneFirebase.persistenceReady).then(() => {
    auth.onAuthStateChanged(user => {
      currentUid = user?.uid || '';
      reset();
      if (!currentUid) {
        $('dwStatus').textContent = 'Sign in to Sankalp to view your developer wallet.';
        return;
      }
      $('dwStatus').textContent = 'Loading verified wallet state…';
      void refresh(user).catch(error => {
        if (currentUid === user.uid) {
          reset();
          $('dwStatus').textContent = error.message || 'Could not check wallet.';
        }
      });
    }, () => {
      reset();
      $('dwStatus').textContent = 'Could not verify account identity.';
    });
  }).catch(() => {
    reset();
    $('dwStatus').textContent = 'Could not initialize authentication.';
  });
})();
