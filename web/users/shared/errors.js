// One place that turns a failure into a sentence a person can read.
//
// Every screen used to invent its own. Some of them showed the machinery: "Sankalp backend
// returned 401", or the raw text of whatever the browser or Firebase threw. A status code is
// not an explanation, and a stack-shaped string is not an apology - both read as broken
// software even when the cause is as ordinary as an expired session.
//
// The rule: the user gets a sentence and, where it helps, the one action that fixes it. The
// detail goes to the console, where it is useful and nobody has to read it.
(() => {
  const BY_STATUS = {
    400: 'Sankalp could not read that request. Please try again.',
    401: 'Please sign in again — your session has ended.',
    403: 'This account is not allowed to do that.',
    404: 'That is no longer there.',
    408: 'The request took too long. Please try again.',
    409: 'That changed while you were working. Reload and try again.',
    413: 'That file is too large to send.',
    422: 'Some of what you sent was not understood. Please check it and try again.',
    429: 'Too many requests just now. Please wait a moment and try again.',
    500: 'Something went wrong on our side. Please try again.',
    502: 'Sankalp is having trouble reaching its services. Please try again.',
    503: 'Sankalp is briefly unavailable. Please try again in a moment.',
    504: 'Sankalp took too long to answer. Please try again.'
  };

  const BY_CODE = {
    'auth/invalid-email': 'That email address does not look right.',
    'auth/user-disabled': 'This account has been disabled.',
    'auth/user-not-found': 'No account matches that email address.',
    'auth/wrong-password': 'That password is not correct.',
    'auth/invalid-credential': 'That email or password is not correct.',
    'auth/too-many-requests': 'Too many attempts. Please wait a few minutes and try again.',
    'auth/network-request-failed': 'The network dropped. Check your connection and try again.',
    'auth/popup-closed-by-user': 'The sign-in window closed before it finished.',
    'auth/cancelled-popup-request': 'The sign-in window closed before it finished.',
    'auth/requires-recent-login': 'Please sign in again to finish that.'
  };

  const GENERIC = 'Something went wrong. Please try again.';

  function fromStatus(status) {
    return BY_STATUS[Number(status)] || GENERIC;
  }

  function fromError(error) {
    const code = String(error?.code || '').trim();

    if (code && BY_CODE[code]) {
      return BY_CODE[code];
    }

    // A fetch that never reached the server throws a TypeError with no status. That is what a
    // blocked cross-origin call and a dead network both look like, and the two have the same
    // next step for the person reading it.
    if (error instanceof TypeError) {
      return 'Sankalp could not reach the server. Check your connection and try again.';
    }

    // A plain Error carries the sentence the code wrote for it - "No Sankalp account was found
    // for this mobile number.", "Unable to reach the OTP service.", "OTP service request timed
    // out.". Those were all being replaced with "Something went wrong. Please try again.", which
    // tells the person nothing and hid the reason from us too.
    //
    // The Firebase SDK's own messages are excluded: they read like "Firebase: Error
    // (auth/...)" and are a code, not a sentence.
    const message = String(error?.message || '').trim();

    if (message && message.length <= 200 && !/^Firebase:/i.test(message)) {
      return message;
    }

    return GENERIC;
  }

  function report(error, where) {
    if (error) {
      console.warn(`Sankalp: ${where || 'request'} failed`, error);
    }
  }

  window.sankalpError = { fromStatus, fromError, report };
})();
