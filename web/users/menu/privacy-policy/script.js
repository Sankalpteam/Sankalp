(() => {
  window.initMenuPrivacyPolicy = function () {
    const section = document.querySelector(
      '[data-menu-section="privacy-policy"]'
    );

    if (!section) {
      throw new Error('Privacy Policy content is unavailable.');
    }

    const closeButton = section.querySelector('[data-close]');

    closeButton?.addEventListener('click', () => {
      window.closeModal?.();
    });

    return true;
  };
})();
