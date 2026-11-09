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

  function indexRef(conversationId) {
    return firebase().database.ref('users').child(uid()).child('chats')
      .child(conversationId);
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

    if (role === 'user') {
      parent.title = text.slice(0, 60);
    }

    await conversation.set(parent, { merge: true });

    const snapshot = await conversation.get();
    const existing = snapshot.exists ? snapshot.data() || {} : {};
    const title = String(existing.title || '').trim() ||
      (role === 'user' ? text.slice(0, 60) : 'New chat');
    const messageCount = Number(existing.messageCount || 0) + 1;

    const messageRef = conversation.collection('messages').doc();
    const batch = db.batch();

    batch.set(messageRef, { role, content: text, createdAt: now });
    batch.set(conversation, {
      userId: user,
      title,
      updatedAt: now,
      messageCount,
      closed: false,
      closedAt: null
    }, { merge: true });

    await batch.commit();

    // The index is only a pointer; a failure here must not fail the save.
    try {
      await indexRef(conversationId).set(true);
    } catch (error) {
      console.warn('Sankalp chat index update failed:', error);
    }
  }

  async function loadConversation(conversationId) {
    const db = firestore();
    const snapshot = await db.collection(COLLECTION).doc(conversationId).get();

    if (!snapshot.exists) {
      return null;
    }

    const messages = await snapshot.ref
      .collection('messages')
      .orderBy('createdAt', 'asc')
      .get();

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
    const snapshot = await db.collection(COLLECTION)
      .where('userId', '==', uid())
      .limit(50)
      .get();

    if (snapshot.empty) {
      return null;
    }

    const latest = snapshot.docs
      .map(doc => ({ id: doc.id, data: doc.data() || {} }))
      .sort((a, b) => {
        const at = a.data.updatedAt?.toMillis?.() || 0;
        const bt = b.data.updatedAt?.toMillis?.() || 0;
        return bt - at;
      })[0];

    return loadConversation(latest.id);
  }

  async function deleteConversation(conversationId) {
    const db = firestore();
    const conversation = db.collection(COLLECTION).doc(conversationId);
    const messages = await conversation.collection('messages').get();
    const batch = db.batch();

    messages.docs.forEach(doc => batch.delete(doc.ref));
    batch.delete(conversation);
    await batch.commit();

    try {
      await indexRef(conversationId).remove();
    } catch (error) {
      console.warn('Sankalp chat index remove failed:', error);
    }

    // The account's copy is gone; the backend keeps its own copy of the same conversation,
    // keyed by this id, and it is that copy that answers the next message. Deleting only here
    // left the backend believing the thread was still open.
    try {
      const user = window.firebase?.auth?.().currentUser;

      if (user) {
        const token = await user.getIdToken(false);
        const response = await fetch(
          `https://indooneai.duckdns.org/api/chat/conversations/${encodeURIComponent(conversationId)}`,
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
    const snapshot = await db.collection(COLLECTION)
      .where('userId', '==', uid())
      .limit(capped)
      .get();

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

  window.IndooneCloudChat = {
    appendMessage,
    loadConversation,
    loadLatest,
    listConversations,
    deleteConversation
  };
})();
