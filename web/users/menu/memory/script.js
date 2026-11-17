window.initMenuMemory = function () {
  const list = document.getElementById('menuMemoryList');
  const status = document.getElementById('menuMemoryStatus');
  const count = document.getElementById('menuMemoryCount');
  const addButton = document.getElementById('menuMemoryAdd');
  const dialog = document.getElementById('menuMemoryDialog');
  const dialogStatus = document.getElementById('menuMemoryDialogStatus');
  const keyInput = document.getElementById('menuMemoryKey');
  const valueInput = document.getElementById('menuMemoryValue');
  const cancelButton = document.getElementById('menuMemoryDialogCancel');
  const saveButton = document.getElementById('menuMemoryDialogSave');

  function setStatus(text) {
    if (status) {
      status.textContent = text || '';
    }
  }

  function setDialogStatus(text) {
    if (dialogStatus) {
      dialogStatus.textContent = text || '';
    }
  }

  // A memory is stored under its key, and the key is a storage detail -
  // "favourite_colour". The person reading the list did not type that, so the
  // label is the key made readable. The key itself is untouched.
  function labelFor(key) {
    const words = String(key || '').replace(/[_-]+/g, ' ').trim();

    if (!words) {
      return key;
    }

    return words.charAt(0).toUpperCase() + words.slice(1);
  }

  function openDialog() {
    if (keyInput) keyInput.value = '';
    if (valueInput) valueInput.value = '';
    setDialogStatus('');
    dialog?.removeAttribute('hidden');
    keyInput?.focus();
  }

  function closeDialog() {
    dialog?.setAttribute('hidden', '');
  }

  function row(entryKey, entryValue) {
    const wrap = document.createElement('div');
    wrap.className = 'mm-row';

    const text = document.createElement('span');
    text.className = 'mm-row-text';

    const title = document.createElement('b');
    title.textContent = labelFor(entryKey);

    const body = document.createElement('span');
    body.textContent = entryValue;

    text.appendChild(title);
    text.appendChild(body);

    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'mm-delete';
    remove.setAttribute('aria-label', 'Delete ' + labelFor(entryKey));
    remove.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"'
      + ' stroke-width="1.8" stroke-linecap="round" aria-hidden="true">'
      + '<path d="M5 7h14M10 7V5h4v2M6.5 7l1 12h9l1-12"></path></svg>';

    remove.addEventListener('click', async () => {
      try {
        await window.IndooneUserData.deleteMemory(entryKey);
        void load();
      } catch (error) {
        setStatus(error?.message || 'Could not delete memory.');
      }
    });

    wrap.appendChild(text);
    wrap.appendChild(remove);
    return wrap;
  }

  async function load() {
    setStatus('');

    try {
      const memory = await window.IndooneUserData.readMemory();
      const entries = Object.keys(memory || {})
        .map(key => ({ key, value: String(memory[key]?.value || '') }))
        .filter(entry => entry.value.trim())
        .sort((a, b) => a.key.localeCompare(b.key));

      if (list) {
        list.textContent = '';

        if (!entries.length) {
          const empty = document.createElement('p');
          empty.className = 'mm-empty';
          empty.textContent = 'No memories saved yet.';
          list.appendChild(empty);
        } else {
          entries.forEach(entry => list.appendChild(row(entry.key, entry.value)));
        }
      }

      if (count) {
        if (entries.length) {
          count.textContent = entries.length + ' saved';
          count.removeAttribute('hidden');
        } else {
          count.setAttribute('hidden', '');
        }
      }
    } catch (error) {
      setStatus(error?.message || 'Could not load memory.');
    }
  }

  addButton?.addEventListener('click', openDialog);
  cancelButton?.addEventListener('click', closeDialog);

  saveButton?.addEventListener('click', async () => {
    const key = (keyInput?.value || '').trim();
    const value = (valueInput?.value || '').trim();

    if (!key || !value) {
      setDialogStatus('A key and a value are both needed.');
      return;
    }

    setDialogStatus('');

    try {
      await window.IndooneUserData.setMemory(key, value);
      closeDialog();
      await load();
    } catch (error) {
      setDialogStatus(error?.message || 'Could not save memory.');
    }
  });

  // The key's own rule, which the account enforces: letters, digits, - and _.
  keyInput?.addEventListener('input', () => setDialogStatus(''));

  void load();
};
