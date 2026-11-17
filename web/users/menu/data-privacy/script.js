window.initMenuDataPrivacy = function () {
  // The app's Data & Privacy cards are static: they carry no chevron, no badge
  // and open nothing (DataPrivacyMenuScreen.kt). The rows used to be buttons
  // that navigated to Profile, Memory, Chat History and Security; they are
  // plain rows in the fragment now, so there is nothing to wire.
  return true;
};
