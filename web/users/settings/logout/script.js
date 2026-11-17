window.initMenuLogout = function () {
  const modal = document.getElementById('modal');

  if (!modal) {
    return;
  }

  modal
    .querySelectorAll('[data-open-nested]')
    .forEach(button => {
      button.addEventListener('click', () => {
        window.openMenuNested(button.dataset.openNested);
      });
    });
};
