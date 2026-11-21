// The web counterpart of android/home/message/CloudChatRepository.kt.
//
// Same shape: the conversation and its messages live in Firestore, and the
// Realtime Database keeps a light index at users/<uid>/chats/<id>. Without this
// the web kept the thread in the browser only, so it never reached another
// device and the Chat History page had nothing to list.
(() => {
  const COLLECTION = 'conversations';

  function firebase() {
    return window.IndooneFirebase;
  }

  function uid() {
    const user = firebase()?.auth?.currentUser;

    if (!user) {
      throw new Error('Please sign in again.');
    }

    return user.uid;
  }

  function firestore() {
    const db = firebase()?.firestore;

    if (!db) {
      throw new Error('Sankalp storage is unavailable.');
    }

    return db;
  }

  function indexRef(conversationId, userId = uid()) {
    return firebase().database.ref('users').child(userId).child('chats')
      .child(conversationId);
  }

  function requireCurrentOwner(userId) {
    if (uid() !== userId) {
      throw new Error('Account changed during chat operation. Please reload your chats.');
    }
  }

  // The app writes the parent with a merge so it never resets messageCount.
  async function appendMessage(conversationId, role, content) {
    const text = String(content || '').trim();

    if (!conversationId || !text) {
      return;
    }

    const db = firestore();
    const user = uid();
    const conversation = db.collection(COLLECTION).doc(conversationId);
    const now = firebase().fieldValue.serverTimestamp();

    const parent = {
      userId: user,
      updatedAt: now
    };

    // Do not overwrite a user-renamed title each time a new prompt arrives.
    // The initial title is set only after the owner document exists.
    await conversation.set(parent, { merge: true });

    const snapshot = await conversation.get();
    requireCurrentOwner(user);
    const existing = snapshot.exists ? snapshot.data() || {} : {};
    if (existing.userId !== user) {
      throw new Error('Conversation does not belong to this account.');
    }
    const oldTitle = String(existing.title || '').trim();

    const messageRef = conversation.collection('messages').doc();
    const batch = db.batch();

    batch.set(messageRef, { role, content: text, createdAt: now });
    // The count is incremented by the database, not by us.
    //
    // Reading it, adding one and writing it back is a race: the user's turn and the answer to
    // it are appended without waiting for each other, so both reads saw the same number and
    // both wrote it plus one. A two-message thread could end up recorded as one.
    const metadata = {
      userId: user,
      updatedAt: now,
      messageCount: firebase().fieldValue.increment(1),
      closed: false,
      closedAt: null
    };
    // Only the first user message may supply the default title. Assistant
    // saves must not race with user saves or overwrite manual renames.
    if (role === 'user' && (!oldTitle || oldTitle === 'New chat')) {
      metadata.title = text.slice(0, 60);
    }
    batch.set(conversation, metadata, { merge: true });

    await batch.commit();

    // The index is only a pointer; a failure here must not fail the save.
    try {
      requireCurrentOwner(user);
      await indexRef(conversationId, user).set(true);
    } catch (error) {
      console.warn('Sankalp chat index update failed:', error);
    }
  }

  async function loadConversation(conversationId) {
    const db = firestore();
    const user = uid();
    const snapshot = await db.collection(COLLECTION).doc(conversationId).get();
    requireCurrentOwner(user);

    if (!snapshot.exists) {
      return null;
    }
    if (snapshot.data()?.userId !== user) {
      throw new Error('Conversation does not belong to this account.');
    }

    const messages = await snapshot.ref
      .collection('messages')
      .orderBy('createdAt', 'asc')
      .get();
    requireCurrentOwner(user);

    return {
      id: conversationId,
      title: snapshot.data()?.title || 'Conversation',
      messages: messages.docs
        .map(doc => ({
          role: doc.data()?.role || 'assistant',
          content: doc.data()?.content || ''
        }))
        .filter(item => item.content)
    };
  }

  async function loadLatest() {
    const db = firestore();
    let snapshot;
    try {
      snapshot = await db.collection(COLLECTION)
        .where('userId', '==', uid())
        .orderBy('updatedAt', 'desc')
        .limit(1)
        .get();
    } catch (error) {
      // Existing deployments may lack the userId/updatedAt composite index.
      console.warn('Sankalp latest chat query requires an index:', error);
      snapshot = await db.collection(COLLECTION)
        .where('userId', '==', uid())
        .limit(50)
        .get();
    }

    if (snapshot.empty) return null;
    const latest = snapshot.docs.slice().sort((a, b) =>
      (b.data()?.updatedAt?.toMillis?.() || 0) -
      (a.data()?.updatedAt?.toMillis?.() || 0)
    )[0];
    return loadConversation(latest.id);
  }

  async function deleteConversation(conversationId) {
    if (!conversationId) return;
    const db = firestore();
    const user = uid();
    const conversation = db.collection(COLLECTION).doc(conversationId);
    const ownerSnapshot = await conversation.get();
    requireCurrentOwner(user);
    if (ownerSnapshot.exists && ownerSnapshot.data()?.userId !== user) {
      throw new Error('Conversation does not belong to this account.');
    }

    // Firestore allows at most 500 writes per batch. Keep the parent document
    // until all children are gone, so interrupted cleanup can be retried.
    if (ownerSnapshot.exists) {
      while (true) {
        requireCurrentOwner(user);
        const messages = await conversation.collection('messages').limit(400).get();
        requireCurrentOwner(user);
        if (messages.empty) break;
        const batch = db.batch();
        messages.docs.forEach(doc => batch.delete(doc.ref));
        await batch.commit();
      }
      requireCurrentOwner(user);
      await conversation.delete();
    }

    try {
      requireCurrentOwner(user);
      await indexRef(conversationId, user).remove();
    } catch (error) {
      console.warn('Sankalp chat index remove failed:', error);
    }

    // The account's copy is gone; the backend keeps its own copy of the same conversation,
    // keyed by this id, and it is that copy that answers the next message. Deleting only here
    // left the backend believing the thread was still open.
    try {
      const account = window.firebase?.auth?.().currentUser;

      if (account && account.uid === user) {
        const token = await account.getIdToken(false);
        requireCurrentOwner(user);
        const response = await fetch(
          `${String(window.SANKALP_BACKEND || '').replace(/\/+$/, '')}/api/chat/conversations/${encodeURIComponent(conversationId)}`,
          { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } },
        );

        // 404 is acceptable: the backend may never have stored this thread, and the delete
        // that matters - the account's - has already succeeded.
        if (!response.ok && response.status !== 404) {
          console.warn('Sankalp backend conversation delete failed:', response.status);
        }
      }
    } catch (error) {
      console.warn('Sankalp backend conversation delete failed:', error);
    }
  }

  // The web counterpart of CloudChatRepository.loadConversations(limit). The
  // app sorts the same query by updatedAt descending and falls back to
  // "New chat" for a document with no title yet. The welcome screen reads the
  // first two of these to offer "Pick up where you left off".
  async function listConversations(limit = 50) {
    const db = firestore();
    const capped = Math.min(Math.max(Number(limit) || 50, 1), 100);
    // Newest first, from the database.
    //
    // The query used to take an arbitrary fifty - Firestore returns documents in id order when
    // no orderBy is given - and only then sorted them here. So once a person had more than
    // fifty threads, the fifty that came back were not the newest ones: recent conversations
    // could be missing from the list while old ones showed up.
    //
    // orderBy on a second field needs a composite index. If the index has not been created yet
    // the query throws, so it falls back to the old shape rather than leaving the user with an
    // empty history.
    let snapshot;

    try {
      snapshot = await db.collection(COLLECTION)
        .where('userId', '==', uid())
        .orderBy('updatedAt', 'desc')
        .limit(capped)
        .get();
    } catch (error) {
      console.warn('Sankalp ordered conversation query failed, falling back:', error?.message || error);
      snapshot = await db.collection(COLLECTION)
        .where('userId', '==', uid())
        .limit(capped)
        .get();
    }

    return snapshot.docs
      .map(doc => ({ id: doc.id, data: doc.data() || {} }))
      .sort((a, b) => {
        const at = a.data.updatedAt?.toMillis?.() || 0;
        const bt = b.data.updatedAt?.toMillis?.() || 0;
        return bt - at;
      })
      .map(item => ({
        id: item.id,
        title: String(item.data.title || '').trim() || 'New chat',
        messageCount: Number(item.data.messageCount) || 0,
        updatedAt: item.data.updatedAt?.toMillis?.() || 0
      }));
  }

  async function renameConversation(conversationId, title) {
    const value = String(title || '').trim().slice(0, 120);
    if (!conversationId || !value) return;
    const user = uid();
    const conversation = firestore().collection(COLLECTION).doc(conversationId);
    const snapshot = await conversation.get();
    requireCurrentOwner(user);
    if (!snapshot.exists || snapshot.data()?.userId !== user) {
      throw new Error('Conversation does not belong to this account.');
    }
    // update() refuses to invent an orphan conversation document.
    await conversation.update({ title: value });
  }

  window.IndooneCloudChat = {
    appendMessage,
    loadConversation,
    loadLatest,
    listConversations,
    deleteConversation,
    renameConversation
  };
})();
