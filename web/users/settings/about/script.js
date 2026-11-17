/* About Sankalp. The web counterpart of android/settings/about/AboutScreen.kt,
 * which is the Settings row's About - not the drawer's About, which is
 * android/menu/about/MenuAboutScreen.kt and opens under the 'about' key.
 *
 * The loader fetches the fragment into #modal, calls this initializer, then
 * shows the page. The × and the Done button both carry data-close, which
 * menu/script.js handles for every feature.
 */
window.initSettingsAbout = function () {
  const modal = document.getElementById('modal');

  if (!modal) {
    return;
  }

  // The app shows "Build " + CrashLogStore.buildNumber, which is the package's
  // versionCode (CrashLogStore.kt). The web's update.json carries the same
  // number, so the Version row reads it from there.
  const versionNode = modal.querySelector('[data-about-version]');

  async function fillVersion() {
    if (!versionNode) {
      return;
    }

    try {
      const response = await fetch('update.json', { cache: 'no-store' });

      if (!response.ok) {
        versionNode.textContent = '—';
        return;
      }

      const metadata = await response.json();
      const code = String(metadata.versionCode || '').trim();

      versionNode.textContent = code ? `Build ${code}` : '—';
    } catch (_) {
      versionNode.textContent = '—';
    }
  }

  fillVersion();

  // The app's Updates row runs its update check (AboutMetaRow's
  // onCheckForUpdates). The web's rendering of that check is the update screen,
  // which this row opens.
  modal
    .querySelector('[data-about-updates]')
    ?.addEventListener('click', () => {
      window.openMenuFeature?.('update');
    });
};
