// The plus sheet. Native uses a Compose bottom sheet with three options; this
// is the same three, and it hands the chosen option to the matching folder.
(() => {
  let host = null;

  function close() {
    host?.setAttribute('hidden', '');
  }

  function open() {
    host?.removeAttribute('hidden');
  }

  function isOpen() {
    return host ? !host.hasAttribute('hidden') : false;
  }

  function init(element) {
    host = element;

    host.querySelectorAll('[data-plus]').forEach(button => {
      button.addEventListener('click', () => {
        const kind = button.dataset.plus;
        close();

        if (kind === 'camera') {
          window.IndoonePlusCamera?.pick?.();
        } else if (kind === 'photos') {
          window.IndoonePlusPhotos?.pick?.();
        } else if (kind === 'files') {
          window.IndoonePlusFiles?.pick?.();
        }
      });
    });

    host.addEventListener('click', event => {
      if (event.target === host) {
        close();
      }
    });
  }

  window.IndoonePlusSheet = { init, open, close, isOpen };
})();
