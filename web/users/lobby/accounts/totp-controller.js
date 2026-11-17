window.IndooneTotpController = (() => {
  let timerId = null;

  function remaining(period = 30) {
    const now = Math.floor(Date.now() / 1000);
    return period - (now % period);
  }

  async function refreshAccount(account) {
    if (!account?.secret) return;

    try {
      const period = Number(account.period || 30);
      const counter = Math.floor(Date.now() / 1000 / period);
      const code = await TOTP.generate(
        account.secret,
        counter,
        Number(account.digits || 6),
        account.algorithm || 'SHA1'
      );

      // The app shows the code exactly as generated, unbroken
      // (AccountsScreen.kt and AccountDetailsScreen.kt draw account.code as-is).
      account.code = code;
      account.seconds = remaining(period);
    } catch (_) {
      account.code = '------';
      account.seconds = remaining(Number(account.period || 30));
    }
  }

  async function refreshAll() {
    await Promise.all(indooneState.accounts.map(refreshAccount));
    renderAccounts();

    if (IndoonePersistence.isUnlocked()) {
      try {
        await IndoonePersistence.persistCurrent();
      } catch (_) {}
    }
  }

  function start() {
    clearInterval(timerId);

    if (!IndoonePersistence.isUnlocked()) return;

    refreshAll();
    timerId = setInterval(refreshAll, 1000);
  }

  return {
    refreshAccount,
    refreshAll,
    start
  };
})();
