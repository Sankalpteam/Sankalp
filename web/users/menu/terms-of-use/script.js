window.initMenuTermsOfUse = function () {
  const modal = document.getElementById('modal');

  if (!modal) return false;

  const closeButton = modal.querySelector('[data-close]');

  closeButton?.setAttribute(
    'aria-label',
    'Close Terms of Use'
  );

  return true;
};
