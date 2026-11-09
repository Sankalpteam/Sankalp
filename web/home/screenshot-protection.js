(() => {
  function isHomeAccountsVisible() {
    const content = document.getElementById('content');
    const subPage = document.getElementById('homeSubPage');
    const homeNav = document.getElementById('homeNav');

    return (
      !!content &&
      !content.hidden &&
      !!homeNav?.classList.contains('active') &&
      !!subPage?.hidden
    );
  }

  function setNative(enabled) {
    try {
      window.IndooneNative?.setSecureScreen?.(!!enabled);
    } catch (error) {
      console.warn(
        'Sankalp screenshot protection native bridge unavailable:',
        error
      );
    }
  }

  function sync() {
    setNative(isHomeAccountsVisible());
  }

  function observeNavigation() {
    const observer = new MutationObserver(sync);

    [
      'homeNav',
      'lobbyNav',
      'settingsNav'
    ].forEach(id => {
      const node = document.getElementById(id);

      if (node) {
        observer.observe(node, {
          attributes: true,
          attributeFilter: ['class']
        });
      }
    });

    const content = document.getElementById('content');

    if (content) {
      observer.observe(content, {
        attributes: true,
        attributeFilter: ['hidden']
      });
    }

    const subPage = document.getElementById('homeSubPage');

    if (subPage) {
      observer.observe(subPage, {
        attributes: true,
        attributeFilter: ['hidden']
      });
    }
  }

  function init() {
    observeNavigation();
    sync();
  }

  window.IndooneHomeScreenshot = {
    sync
  };

  if (document.readyState === 'loading') {
    document.addEventListener(
      'DOMContentLoaded',
      init,
      { once: true }
    );
  } else {
    init();
  }
})();
