// The web counterpart of android/menu/data/UserDataRepository.kt. Profile and
// memory live in the same Realtime Database paths the app uses, so the same
// account shows the same data in both.
(() => {
  function user() {
    return window.IndooneFirebase?.auth?.currentUser || null;
  }

  function requireUser() {
    const current = user();

    if (!current) {
      throw new Error('Please sign in again.');
    }

    return current;
  }

  function db() {
    const database = window.IndooneFirebase?.database;

    if (!database) {
      throw new Error('Sankalp storage is unavailable.');
    }

    return database;
  }

  function userRef(child) {
    const uid = requireUser().uid;
    return db().ref('users').child(uid).child(child);
  }

  async function readProfile() {
    const snapshot = await userRef('profile').get();
    return snapshot.val() || {};
  }

  async function updateProfile({ name, nickname, profession }) {
    await userRef('profile').update({
      name: name || '',
      nickname: nickname || '',
      profession: profession || '',
      updated_at: Date.now()
    });
  }

  async function readMemory() {
    const snapshot = await userRef('memory').get();
    return snapshot.val() || {};
  }

  async function setMemory(key, value) {
    if (!/^[A-Za-z0-9_-]+$/.test(key)) {
      throw new Error('A memory key can use letters, digits, - and _ only.');
    }

    await userRef('memory').child(key).set({
      value,
      updated_at: Date.now()
    });
  }

  async function deleteMemory(key) {
    await userRef('memory').child(key).remove();
  }

  async function listChats() {
    const snapshot = await userRef('chats').get();
    const index = snapshot.val() || {};

    return Object.keys(index).map(id => ({ id, savedAt: index[id] }));
  }

  async function deleteChat(id) {
    await userRef('chats').child(id).remove();
  }

  window.IndooneUserData = {
    readProfile,
    updateProfile,
    readMemory,
    setMemory,
    deleteMemory,
    listChats,
    deleteChat
  };
})();
