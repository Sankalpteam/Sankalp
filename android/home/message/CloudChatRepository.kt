package com.indoone.home.message

import com.google.android.gms.tasks.Tasks
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class CloudChatMessage(
    val role: String,
    val content: String,
)

data class CloudChatConversation(
    val id: String,
    val title: String,
    val messages: List<CloudChatMessage>,
    val messageCount: Int,
    val closed: Boolean,
    val updatedAtMillis: Long,
)

class CloudChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val realtimeDatabase: FirebaseDatabase = FirebaseDatabase.getInstance(),
) {
    private val uid: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Please sign in again.")

    private fun chatIndex(userId: String = uid): DatabaseReference =
        realtimeDatabase.reference.child("users").child(userId).child("chats")

    fun addConversationIdListener(
        onChanged: (Set<String>) -> Unit,
        onError: (String) -> Unit,
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                onChanged(snapshot.children.mapNotNull { it.key }.toSet())
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                onError(error.message.ifBlank { "Chat sync could not be loaded right now." })
            }
        }
        chatIndex().addValueEventListener(listener)
        return listener
    }

    fun removeConversationIdListener(listener: ValueEventListener) {
        chatIndex().removeEventListener(listener)
    }

    suspend fun appendMessage(
        conversationId: String,
        role: String,
        content: String,
    ) = withContext(Dispatchers.IO) {
        require(conversationId.isNotBlank()) { "conversationId cannot be empty" }
        require(role == "user" || role == "assistant") { "message role must be user or assistant" }
        require(content.isNotBlank()) { "message content cannot be empty" }

        val user = auth.currentUser ?: throw IllegalStateException("Please sign in again.")
        Tasks.await(user.getIdToken(true))
        val userId = user.uid
        val conversation = firestore.collection("conversations").document(conversationId)
        val now = FieldValue.serverTimestamp()

        // Establish ownership without reading a missing document first. The merge
        // write creates the parent when needed and, for an existing document,
        // changes only owner/title/timestamp metadata; it never resets messageCount.
        val parentValues = mutableMapOf<String, Any?>(
            "userId" to userId,
            "updatedAt" to now,
        )
        if (role == "user") {
            parentValues["title"] = content.trim().take(60)
        }
        Tasks.await(conversation.set(parentValues, SetOptions.merge()))

        val snapshot = Tasks.await(conversation.get())
        if (!snapshot.exists()) {
            throw IllegalStateException("The conversation could not be created.")
        }
        if (snapshot.getString("userId").orEmpty() != userId) {
            throw SecurityException("Conversation does not belong to this account.")
        }

        val existingCount = snapshot.getLong("messageCount")?.toInt() ?: 0

        val messageRef = conversation.collection("messages").document(UUID.randomUUID().toString())
        val batch = firestore.batch()
        batch.set(
            messageRef,
            mapOf(
                "role" to role,
                "content" to content.trim(),
                "createdAt" to now,
            ),
        )

        val newCount = existingCount + 1
        batch.set(
            conversation,
            mapOf(
                "userId" to userId,
                "title" to snapshot.getString("title").orEmpty().ifBlank {
                    if (role == "user") content.trim().take(60) else "New chat"
                },
                "updatedAt" to now,
                "messageCount" to newCount,
                "closed" to false,
                "closedAt" to null,
            ),
            SetOptions.merge(),
        )
        Tasks.await(batch.commit())

        // Realtime Database is only a lightweight index. A transient index failure
        // must not turn a successful Firestore save into a user-visible save error.
        runCatching {
            Tasks.await(chatIndex(userId).child(conversationId).setValue(true))
        }.onFailure {
            Log.w(
                "CloudChatRepository",
                "Realtime chat index update failed after Firestore save; it will be repaired later.",
                it,
            )
        }
    }

    suspend fun appendExchange(
        conversationId: String,
        userMessage: String,
        assistantReply: String,
    ) = withContext(Dispatchers.IO) {
        require(conversationId.isNotBlank()) { "conversationId cannot be empty" }
        require(userMessage.isNotBlank()) { "userMessage cannot be empty" }
        require(assistantReply.isNotBlank()) { "assistantReply cannot be empty" }

        // Preserve the existing API while using the same individually secured
        // message persistence path as the chat screen.
        appendMessage(conversationId, "user", userMessage)
        appendMessage(conversationId, "assistant", assistantReply)
    }

    suspend fun loadConversations(limit: Int = 50): List<CloudChatConversation> = withContext(Dispatchers.IO) {
        val userId = uid
        val documents = Tasks.await(
            firestore.collection("conversations")
                .whereEqualTo("userId", userId)
                .limit(limit.coerceIn(1, 100).toLong())
                .get(),
        ).documents.sortedByDescending {
            it.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
        }

        val indexedIds = documents.map { it.id }.toSet()
        val currentIndex = Tasks.await(chatIndex(userId).get()).children.mapNotNull { it.key }.toSet()
        val missingIds = indexedIds - currentIndex
        if (missingIds.isNotEmpty()) {
            Tasks.await(
                chatIndex(userId).updateChildren(
                    missingIds.associateWith { true },
                ),
            )
        }

        documents.map { document ->
            CloudChatConversation(
                id = document.id,
                title = document.getString("title").orEmpty().ifBlank { "New chat" },
                messages = emptyList(),
                messageCount = document.getLong("messageCount")?.toInt() ?: 0,
                closed = document.getBoolean("closed") ?: false,
                updatedAtMillis = document.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
            )
        }
    }

    suspend fun loadConversation(conversationId: String): CloudChatConversation? = withContext(Dispatchers.IO) {
        require(conversationId.isNotBlank()) { "conversationId cannot be empty" }
        val userId = uid
        val document = Tasks.await(
            firestore.collection("conversations").document(conversationId).get(),
        )
        if (!document.exists()) return@withContext null
        if (document.getString("userId") != userId) {
            throw SecurityException("Conversation does not belong to this account.")
        }

        val messages = Tasks.await(
            document.reference.collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get(),
        ).documents.mapNotNull { message ->
            val role = message.getString("role") ?: return@mapNotNull null
            val content = message.getString("content") ?: return@mapNotNull null
            CloudChatMessage(role, content)
        }

        CloudChatConversation(
            id = document.id,
            title = document.getString("title").orEmpty().ifBlank { "New chat" },
            messages = messages,
            messageCount = document.getLong("messageCount")?.toInt() ?: messages.size,
            closed = document.getBoolean("closed") ?: false,
            updatedAtMillis = document.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
        )
    }

    suspend fun loadLatestChat(limit: Int = 50): CloudChatConversation? = withContext(Dispatchers.IO) {
        val userId = uid
        val conversations = Tasks.await(
            firestore.collection("conversations")
                .whereEqualTo("userId", userId)
                .limit(limit.coerceIn(1, 100).toLong())
                .get(),
        ).documents.sortedByDescending {
            it.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
        }

        val document = conversations.firstOrNull() ?: return@withContext null
        val messages = Tasks.await(
            document.reference.collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get(),
        ).documents.mapNotNull { message ->
            val role = message.getString("role") ?: return@mapNotNull null
            val content = message.getString("content") ?: return@mapNotNull null
            CloudChatMessage(role, content)
        }

        CloudChatConversation(
            id = document.id,
            title = document.getString("title").orEmpty().ifBlank { "New chat" },
            messages = messages,
            messageCount = document.getLong("messageCount")?.toInt() ?: messages.size,
            closed = document.getBoolean("closed") ?: false,
            updatedAtMillis = document.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
        )
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        require(conversationId.isNotBlank()) { "conversationId cannot be empty" }
        val userId = uid
        val conversation = firestore.collection("conversations").document(conversationId)
        val snapshot = Tasks.await(conversation.get())
        if (snapshot.exists()) {
            if (snapshot.getString("userId") != userId) {
                throw SecurityException("Conversation does not belong to this account.")
            }

            val messages = Tasks.await(conversation.collection("messages").get()).documents
            val batch = firestore.batch()
            messages.forEach { batch.delete(it.reference) }
            batch.delete(conversation)
            Tasks.await(batch.commit())
        }
        Tasks.await(chatIndex(userId).child(conversationId).removeValue())

        // The account's copy is gone; the backend keeps its own. Without this the thread still
        // exists on the server, and any message that arrives with this id continues it.
        runCatching { ChatApi.deleteConversation(conversationId) }
            .onFailure { Log.w("SankalpChat", "Backend conversation delete failed", it) }
    }

    suspend fun cleanupExpiredConversations(maxAgeDays: Long = 7L) = withContext(Dispatchers.IO) {
        require(maxAgeDays > 0) { "maxAgeDays must be greater than zero" }
        val cutoff = System.currentTimeMillis() - maxAgeDays * 24L * 60L * 60L * 1000L
        val userId = uid
        val documents = Tasks.await(
            firestore.collection("conversations")
                .whereEqualTo("userId", userId)
                .limit(100L)
                .get(),
        ).documents

        documents
            .filter { document ->
                document.getBoolean("closed") == true &&
                    (document.getTimestamp("closedAt")?.toDate()?.time ?: Long.MAX_VALUE) <= cutoff
            }
            .forEach { document ->
                deleteConversation(document.id)
            }
    }

}
