/* The consent a person gives once, and the version they gave it for.
 *
 * One file, one version string. Signup records it, login compares against it, and Settings
 * shows it. When the terms change, CONSENT_VERSION changes here and nowhere else: every
 * screen that asks, asks about the same number.
 *
 * The rule that keeps this from becoming a bug: if we cannot read what was stored, we ask.
 * An unreadable consent is not consent, and a missing check is a person inside the app
 * without having agreed to anything. Reading it wrong in that direction costs one checkbox;
 * reading it wrong the other way costs the agreement itself.
 */
(function () {
  'use strict';

  // Bump when the documents change. The date form is deliberate: a version number that says
  // which month it is reads correctly in a support conversation a year later.
  const CONSENT_VERSION = '2026-11';

  const TERMS_URL = '/legal/terms-of-use.html';
  const PRIVACY_URL = '/legal/privacy-policy.html';

  function database() {
    if (!window.firebase || !firebase.database) return null;
    try {
      return firebase.database();
    } catch (e) {
      return null;
    }
  }

  function currentUid() {
    if (!window.firebase || !firebase.auth) return '';
    const user = firebase.auth().currentUser;
    return user ? String(user.uid || '') : '';
  }

  /**
   * What is stored for this person, or null when there is nothing to read.
   * Never throws: the caller decides what an unknown answer means, and the caller's answer
   * is "ask".
   */
  function readConsent() {
    const uid = currentUid();
    const db = database();
    if (!uid || !db) return Promise.resolve(null);
    return db
      .ref(`users/${uid}/consent`)
      .once('value')
      .then((snapshot) => snapshot.val() || null)
      .catch(() => null);
  }

  /**
   * True when this person has to be asked.
   *
   * Anything other than a matching version - including a failed read, a missing record and a
   * record from an older version - answers true. The only way to get false is to have read a
   * record that says the current version, which is exactly what consent means.
   */
  function needsConsent() {
    return readConsent().then((stored) => {
      if (!stored) return true;
      return (
        String(stored.termsVersion || '') !== CONSENT_VERSION ||
        String(stored.privacyVersion || '') !== CONSENT_VERSION ||
        !stored.termsAcceptedAt ||
        !stored.privacyAcceptedAt
      );
    });
  }

  /** Record what was just agreed to. Returns true when it was written. */
  function recordConsent(options) {
    const uid = currentUid();
    const db = database();
    if (!uid || !db) return Promise.resolve(false);
    const now = Date.now();
    const ageConfirmed = Boolean(options && options.ageConfirmed);
    return db
      .ref(`users/${uid}/consent`)
      .update({
        termsVersion: CONSENT_VERSION,
        termsAcceptedAt: now,
        privacyVersion: CONSENT_VERSION,
        privacyAcceptedAt: now,
        ageConfirmedAt: ageConfirmed ? now : null,
        updatedAt: now,
      })
      .then(() => true)
      .catch(() => false);
  }

  window.SankalpConsent = {
    VERSION: CONSENT_VERSION,
    TERMS_URL,
    PRIVACY_URL,
    readConsent,
    needsConsent,
    recordConsent,
  };
})();
