window.initMenuSecurity = function () {
  // The app's Security sheet is one heading and one paragraph, and
  // web/menu/security/index.html already holds that paragraph word for word
  // from SecurityScreen.kt. This used to overwrite the fragment with a reworded
  // three-paragraph version, which is why the reworded text rendered. There is
  // nothing to do now, so the fragment is left exactly as it is.
  return true;
};
