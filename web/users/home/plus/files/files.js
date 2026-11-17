// Files: any document. The backend reads text out of it and replies about it.
(() => {
  const MAX_FILES = 10;

  function pick() {
    const input = document.createElement('input');
    input.type = 'file';
    input.multiple = true;

    input.addEventListener('change', () => {
      const files = Array.from(input.files || []).slice(0, MAX_FILES);

      files.forEach(file => {
        window.IndooneSend?.attach?.(file);
      });
    });

    input.click();
  }

  window.IndoonePlusFiles = { pick };
})();
