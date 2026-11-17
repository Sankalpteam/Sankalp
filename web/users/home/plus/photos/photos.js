// Photos: the browser equivalent of the native photo picker, scoped to images.
(() => {
  const MAX_IMAGES = 10;

  function pick() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.multiple = true;

    input.addEventListener('change', () => {
      const files = Array.from(input.files || []).slice(0, MAX_IMAGES);

      files.forEach(file => {
        window.IndooneSend?.attach?.(file);
      });
    });

    input.click();
  }

  window.IndoonePlusPhotos = { pick };
})();
