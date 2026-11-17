window.initMenuLogoutThisDevice = function () {
  const confirmButton = document.getElementById('confirmThisDeviceLogout');

  if (!confirmButton) {
    return;
  }

  const clearLocalSession = () => {
    try {
      Object.keys(localStorage).forEach(key => {
        if (key.startsWith('indoone')) {
          localStorage.removeItem(key);
        }
      });
    } catch (_) {}

    try {
      Object.keys(sessionStorage).forEach(key => {
        if (key.startsWith('indoone')) {
          sessionStorage.removeItem(key);
        }
      });
    } catch (_) {}

    try {
      window.IndoonePersistence?.lock?.();
    } catch (_) {}

    try {
      window.IndoonePersistence?.clear?.();
    } catch (_) {}

    try {
    } catch (_) {}
  };

  confirmButton.addEventListener(
    'click',
    async () => {
      try {
        clearLocalSession();
        await window.IndooneFirebase?.auth?.signOut?.();
        closeModal();
        window.location.reload();
      } catch (error) {
        toast(error?.message || 'Could not log out');
      }
    }
  );
};
