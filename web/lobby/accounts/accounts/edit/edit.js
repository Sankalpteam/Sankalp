(() => {
  async function open(account) {
    if (!account?.id) {
      toast('Account not found');
      return;
    }

    try {
      // The account details screen is rendered inside the global overlay.
      // Close it before moving into the Home sub-page so it cannot cover the edit form.
      window.closeModal?.();

      // Make sure the Home sub-page mount is visible before rendering Manual Edit.
      if (window.IndooneHome?.showAddAccount) {
        await window.IndooneHome.showAddAccount({
          push: false
        });
      }

      if (!window.IndooneAddAccount?.showManual) {
        throw new Error(
          'Edit account feature is unavailable.'
        );
      }

      return await window.IndooneAddAccount.showManual({
        push: true,
        id: account.id,
        prefill: account
      });
    } catch (error) {
      console.error(
        'Sankalp account edit navigation failed:',
        error
      );
      toast(
        error?.message ||
          'Could not open Edit Account'
      );
    }
  }

  window.IndooneAccountEdit = {
    open
  };
})();
