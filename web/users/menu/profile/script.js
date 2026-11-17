window.initMenuProfile = function () {
  const form = document.getElementById('menuProfileForm');
  const name = document.getElementById('menuProfileName');
  const nickname = document.getElementById('menuProfileNickname');
  const profession = document.getElementById('menuProfileProfession');
  const status = document.getElementById('menuProfileStatus');
  const details = document.getElementById('menuProfileDetails');
  const initials = document.getElementById('menuProfileInitials');
  const displayName = document.getElementById('menuProfileDisplayName');
  const email = document.getElementById('menuProfileEmail');
  const editButton = document.getElementById('menuProfileEdit');
  const cancelButton = document.getElementById('menuProfileCancel');
  const saveButton = document.getElementById('menuProfileSave');

  const nameValue = document.getElementById('menuProfileNameValue');
  const nicknameValue = document.getElementById('menuProfileNicknameValue');
  const professionValue = document.getElementById('menuProfileProfessionValue');

  let editing = false;

  function setStatus(text, kind) {
    if (!status) {
      return;
    }

    status.textContent = text || '';
    status.classList.toggle('is-ok', kind === 'ok');
    status.classList.toggle('is-error', kind === 'error');
  }

  // The app's displayName: the name, then the nickname, then "Sankalp User".
  function shownName(profile) {
    const full = String(profile.name || '').trim();
    const nick = String(profile.nickname || '').trim();

    return full || nick || 'Sankalp User';
  }

  function shownInitials(label) {
    const letters = label
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(word => word.charAt(0).toUpperCase())
      .join('');

    return letters || 'I';
  }

  function paint(profile) {
    const label = shownName(profile);

    if (displayName) displayName.textContent = label;
    if (initials) initials.textContent = shownInitials(label);

    if (nameValue) nameValue.textContent = String(profile.name || '').trim() || 'Not set';
    if (nicknameValue) nicknameValue.textContent = String(profile.nickname || '').trim() || 'Not set';
    if (professionValue) professionValue.textContent = String(profile.profession || '').trim() || 'Not set';

    if (name) name.value = profile.name || '';
    if (nickname) nickname.value = profile.nickname || '';
    if (profession) profession.value = profile.profession || '';
  }

  function setEditing(next) {
    editing = next;

    details?.toggleAttribute('hidden', editing);
    form?.toggleAttribute('hidden', !editing);
    editButton?.toggleAttribute('hidden', editing);
    cancelButton?.toggleAttribute('hidden', !editing);
    saveButton?.toggleAttribute('hidden', !editing);
  }

  async function load() {
    setStatus('Loading…');

    try {
      const profile = await window.IndooneUserData.readProfile();
      paint(profile);
      setStatus('');
    } catch (error) {
      setStatus(error?.message || 'Could not load your profile.', 'error');
    }
  }

  // The account's own email, which the hero shows under the name.
  const account = window.IndooneFirebase?.auth?.currentUser;

  if (email) {
    // The account's own email. With none it keeps the line the screen had.
    email.textContent = account?.email || 'Your Sankalp profile';
  }

  editButton?.addEventListener('click', () => {
    setStatus('');
    setEditing(true);
  });

  cancelButton?.addEventListener('click', () => {
    setStatus('');
    setEditing(false);
    // Reload the saved values, so a cancelled edit is discarded.
    void load();
  });

  saveButton?.addEventListener('click', event => {
    event.preventDefault();
    form?.requestSubmit?.();
  });

  form?.addEventListener('submit', async event => {
    event.preventDefault();

    if (saveButton) {
      saveButton.disabled = true;
    }

    setStatus('Saving…');

    try {
      await window.IndooneUserData.updateProfile({
        name: name?.value || '',
        nickname: nickname?.value || '',
        profession: profession?.value || ''
      });
      setEditing(false);
      await load();
      setStatus('Profile saved.', 'ok');
    } catch (error) {
      setStatus(error?.message || 'Could not save your profile.', 'error');
    } finally {
      if (saveButton) {
        saveButton.disabled = false;
      }
    }
  });

  setEditing(false);
  void load();
};
