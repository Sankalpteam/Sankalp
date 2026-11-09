/* Check for updates. The web counterpart of android/settings/update.
 *
 * It is a menu feature like the others: menu/script.js fetches the fragment
 * into #modal, calls this initializer, then shows the page. It used to fetch
 * the fragment itself and unhide #overlay, which dimmed the whole app and
 * shrank the screen to a card - the same bug Profile and About had. It does
 * not touch #overlay any more.
 *
 * The app reads the same place this does - the repository's releases, looking
 * for a terminal-build-* tag with indoone-terminal.apk attached. A browser
 * cannot install an APK, so this screen reports what is published and hands
 * over the download link instead of installing anything.
 *
 * data-close on the × and on Done is handled once, globally, by
 * shared/events.js.
 */
(() => {
  const RELEASES_URL =
    'https://api.github.com/repos/Sankalpteam/Sankalp/releases';
  const DOWNLOAD_URL =
    'https://github.com/Sankalpteam/Sankalp/releases/latest';

  function setStatus(node, text, kind) {
    if (!node) {
      return;
    }
    node.textContent = text;
    node.classList.toggle('is-new', kind === 'new');
    node.classList.toggle('is-error', kind === 'error');
  }

  async function currentVersion() {
    try {
      const response = await fetch('update.json', { cache: 'no-store' });

      if (!response.ok) {
        return null;
      }

      const metadata = await response.json();
      const name = String(metadata.versionName || '').trim();
      const code = String(metadata.versionCode || '').trim();

      if (!name) {
        return null;
      }

      return code ? `${name} (${code})` : name;
    } catch (_) {
      return null;
    }
  }

  async function latestBuild() {
    const response = await fetch(
      RELEASES_URL,
      { headers: { Accept: 'application/vnd.github+json' } }
    );

    if (!response.ok) {
      throw new Error(`The release list could not be read (${response.status}).`);
    }

    const releases = await response.json();

    if (!Array.isArray(releases)) {
      throw new Error('The release list came back in an unexpected shape.');
    }

    const build = releases.find(release =>
      String(release.tag_name || '').startsWith('terminal-build-')
    );

    if (!build) {
      return null;
    }

    const apk = (build.assets || []).find(asset =>
      String(asset.name || '') === 'indoone-terminal.apk'
    );

    return {
      tag: String(build.tag_name),
      number: String(build.tag_name).replace('terminal-build-', ''),
      url: (apk && apk.browser_download_url) || build.html_url || DOWNLOAD_URL,
      published: String(build.published_at || '').slice(0, 10),
    };
  }

  window.initMenuUpdate = function () {
    const page = document.getElementById('modal');

    if (!page) {
      return false;
    }

    const status = page.querySelector('[data-update-status]');
    const current = page.querySelector('[data-update-current]');
    const latest = page.querySelector('[data-update-latest]');
    const download = page.querySelector('[data-update-download]');

    void (async () => {
      const here = await currentVersion();

      if (current) {
        current.textContent = here || 'Not stated';
      }

      try {
        const build = await latestBuild();

        if (!build) {
          setStatus(status, 'No build has been published yet.', 'error');
          return;
        }

        if (latest) {
          latest.textContent = `Build ${build.number}`;
        }

        if (download) {
          download.href = build.url;
          download.removeAttribute('hidden');
        }

        setStatus(
          status,
          build.published
            ? `The newest build is ${build.number}, published ${build.published}. Download it below to move to it.`
            : `The newest build is ${build.number}. Download it below to move to it.`,
          'new'
        );
      } catch (error) {
        setStatus(
          status,
          'The update list could not be reached. Open the releases page to see the newest build.',
          'error'
        );

        if (download) {
          download.href = DOWNLOAD_URL;
          download.removeAttribute('hidden');
        }
      }
    })();

    return true;
  };
})();
