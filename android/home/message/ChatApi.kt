package com.indoone.home.message

import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.indoone.BuildConfig
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.io.IOException
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException


// One clickable follow-up the backend offered with a reply. The action is the exact text
// this chip sends as the user's next message, so a button and typing cannot diverge.
data class ActionChip(
    val label: String,
    val action: String,
)

data class ChatResponse(
    val conversationId: String,
    val reply: String,
    val actionChips: List<ActionChip> = emptyList(),
    val sources: List<ChatSource> = emptyList(),
)

// The authenticated /api/agent approval workflow is separate from /api/chat chips.
data class AgentApproval(
    val runId: String,
    val tool: String,
    val payload: String,
    val status: String,
    val expiresAt: String,
)

data class AgentApprovalOutcome(
    val runId: String,
    val status: String,
    val requiresManualReview: Boolean,
)

// One page a research answer came from.
data class ChatSource(val title: String, val url: String)

// The backend returns the pages a research answer used, and this client ignored them: the
// answer arrived as a claim with no evidence while the same answer in the browser carried its
// sources. They are appended to the reply text rather than given a new component, because the
// app already draws a URL in a reply as a link row with a copy button - the same control the
// sources need, in the place the reader is already looking.
internal fun withSources(reply: String, sources: List<ChatSource>): String {
    val usable = sources.filter { it.url.isNotBlank() && !reply.contains(it.url) }

    if (usable.isEmpty()) {
        return reply
    }

    val label = if (usable.size == 1) "Source:" else "Sources:"
    val lines = usable.joinToString("\n") { source ->
        val title = source.title.ifBlank { source.url }
        "$title\n${source.url}"
    }

    return reply.trimEnd() + "\n\n" + label + "\n" + lines
}

// A malformed entry is skipped rather than failing the whole reply.
internal fun parseSources(array: JSONArray?): List<ChatSource> {
    if (array == null) {
        return emptyList()
    }

    val sources = mutableListOf<ChatSource>()

    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val url = item.optString("url").trim()

        if (url.isBlank()) {
            continue
        }

        sources += ChatSource(title = item.optString("title").trim(), url = url)
    }

    return sources
}

// Follow-ups, if the backend sent any. A malformed entry is skipped rather than failing
// the whole reply: the answer matters more than the buttons under it.
internal fun parseActionChips(array: JSONArray?): List<ActionChip> {
    if (array == null) {
        return emptyList()
    }

    val chips = mutableListOf<ActionChip>()

    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val label = item.optString("label").trim()
        val action = item.optString("action").trim()

        if (label.isNotEmpty() && action.isNotEmpty()) {
            chips.add(ActionChip(label, action))
        }
    }

    return chips
}

// The inverse of parseActionChips, for the worker: a turn stored off-screen keeps the
// buttons it was given, so recovering the thread does not silently drop them.
internal fun chipsToJson(chips: List<ActionChip>): String? {
    if (chips.isEmpty()) {
        return null
    }

    val array = JSONArray()

    chips.forEach { chip ->
        array.put(JSONObject().put("label", chip.label).put("action", chip.action))
    }

    return array.toString()
}

data class FileUploadResponse(
    val fileId: String,
    val filename: String,
    val bytes: Int,
    val textPreview: String,
)

data class ChatMediaAttachment(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
)

object ChatApi {
    private const val CONNECT_TIMEOUT_MS = 10_000
    // Local Qwen CPU inference can legitimately take a couple of minutes,
// especially while the model is first loaded into memory. Keep the client
// timeout comfortably above observed inference time so valid replies are not
// reported as timeouts.
private const val READ_TIMEOUT_MS = 300_000
    private const val WARMUP_TIMEOUT_MS = 15_000
    private const val WARMUP_RETRIES = 1
    private const val WARMUP_COOLDOWN_MS = 60_000L
    private const val CHAT_TRANSIENT_RETRIES = 1
    private const val CHAT_RETRY_DELAY_MS = 5_000L
    private const val MAX_UPLOAD_BYTES = 2_000_000
    private const val MAX_CHAT_IMAGE_BYTES = 5_000_000
    private const val MAX_CHAT_DOCUMENT_BYTES = 8_000_000
    private const val MAX_CHAT_TOTAL_ATTACHMENT_BYTES = 20_000_000
    private const val MAX_CHAT_ATTACHMENTS = 10

    @Volatile
    private var lastSuccessfulWarmupAt = 0L

    fun sendMessage(
        message: String,
        conversationId: String? = null,
        fileId: String? = null,
        attachmentFilename: String? = null,
        attachmentMimeType: String? = null,
        attachmentBytes: ByteArray? = null,
        fileIds: List<String> = emptyList(),
        mediaAttachments: List<ChatMediaAttachment> = emptyList(),
    ): ChatResponse {
        val backendUrl = requireBackendUrl()
        val authToken = requireAuthToken()

        // Do not create a burst of health/chat requests for a single user action.
        // Render's edge can protect against request bursts before they reach the
        // application, so a single cooldown-controlled warm-up is safer.
        warmUpBackend(backendUrl)

        val mediaCount = mediaAttachments.size + if (attachmentBytes != null) 1 else 0
        val totalAttachmentCount = fileIds.size + (if (fileId != null) 1 else 0) + mediaCount
        if (totalAttachmentCount > MAX_CHAT_ATTACHMENTS) {
            throw IllegalArgumentException("You can attach up to 10 files per message.")
        }
        if (attachmentBytes != null && mediaAttachments.isNotEmpty()) {
            throw IllegalArgumentException("Use one media attachment format per request.")
        }

        val payload = JSONObject().apply {
            put("message", message)
            conversationId?.takeIf { it.isNotBlank() }?.let { put("conversation_id", it) }
            fileId?.takeIf { it.isNotBlank() }?.let { put("file_id", it) }
            if (fileIds.isNotEmpty()) {
                put("file_ids", JSONArray().apply { fileIds.forEach { put(it) } })
            }
            if (mediaAttachments.isNotEmpty()) {
                var totalBytes = 0
                val attachmentArray = JSONArray()
                mediaAttachments.forEach { attachment ->
                    if (attachment.bytes.isEmpty()) {
                        throw IllegalArgumentException("The selected attachment is empty.")
                    }
                    val safeFilename = attachment.filename.trim().takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("The selected attachment has no filename.")
                    val mimeType = attachment.mimeType.trim().lowercase().takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("The selected attachment type could not be detected.")
                    val maxAttachmentBytes = if (mimeType.startsWith("image/")) {
                        MAX_CHAT_IMAGE_BYTES
                    } else {
                        MAX_CHAT_DOCUMENT_BYTES
                    }
                    if (attachment.bytes.size > maxAttachmentBytes) {
                        val limit = if (mimeType.startsWith("image/")) "5 MB" else "8 MB"
                        throw IllegalArgumentException("This attachment exceeds the $limit limit.")
                    }
                    totalBytes += attachment.bytes.size
                    if (totalBytes > MAX_CHAT_TOTAL_ATTACHMENT_BYTES) {
                        throw IllegalArgumentException("Attachments together exceed the 20 MB upload limit.")
                    }
                    attachmentArray.put(JSONObject().apply {
                        put("filename", safeFilename)
                        put("mime_type", mimeType)
                        put("content_base64", Base64.encodeToString(attachment.bytes, Base64.NO_WRAP))
                    })
                }
                put("attachments", attachmentArray)
            }
            if (attachmentBytes != null) {
                if (fileId != null || fileIds.isNotEmpty()) {
                    throw IllegalArgumentException("Send either a text file or a media attachment, not both.")
                }
                if (attachmentBytes.isEmpty()) {
                    throw IllegalArgumentException("The selected attachment is empty.")
                }
                val safeFilename = attachmentFilename?.trim()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("The selected attachment has no filename.")
                val mimeType = attachmentMimeType?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("The selected attachment type could not be detected.")
                val maxAttachmentBytes = if (mimeType.startsWith("image/")) {
                    MAX_CHAT_IMAGE_BYTES
                } else {
                    MAX_CHAT_DOCUMENT_BYTES
                }
                if (attachmentBytes.size > maxAttachmentBytes) {
                    val limit = if (mimeType.startsWith("image/")) "5 MB" else "8 MB"
                    throw IllegalArgumentException("This attachment exceeds the $limit limit.")
                }
                put("attachment_filename", safeFilename)
                put("attachment_mime_type", mimeType)
                put("attachment_base64", Base64.encodeToString(attachmentBytes, Base64.NO_WRAP))
            }
        }.toString()

        var lastTransientError: ChatApiException? = null
        repeat(CHAT_TRANSIENT_RETRIES + 1) { attempt ->
            try {
                val connection = openConnection("$backendUrl/api/chat", authToken)
                return try {
                    val body = executeJson(connection, payload)
                    val json = JSONObject(body)
                    val reply = json.optString("reply")
                    val returnedConversationId = json.optString("conversation_id")
                    if (returnedConversationId.isBlank()) {
                        throw ChatApiException("Sankalp backend returned no conversation id")
                    }
                    if (reply.isBlank()) {
                        throw ChatApiException("Sankalp backend returned an empty reply")
                    }
                    val sources = parseSources(json.optJSONArray("sources"))
                    ChatResponse(
                        returnedConversationId,
                        withSources(reply, sources),
                        parseActionChips(json.optJSONArray("action_chips")),
                        sources,
                    )
                } finally {
                    connection.disconnect()
                }
            } catch (error: ChatApiException) {
                val isTransient = error.statusCode == 404 ||
                    error.statusCode == 429 ||
                    error.statusCode == 502 ||
                    error.statusCode == 503 ||
                    error.statusCode == 504
                if (!isTransient || attempt >= CHAT_TRANSIENT_RETRIES) {
                    throw error
                }
                lastTransientError = error
                try {
                    Thread.sleep(error.retryAfterMs ?: CHAT_RETRY_DELAY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw error
                }
            }
        }

        throw lastTransientError ?: ChatApiException("Sankalp backend request failed")
    }

    fun uploadTextFile(filename: String, bytes: ByteArray): FileUploadResponse {
        val safeFilename = filename.trim()
        if (safeFilename.isBlank()) throw ChatApiException("Please choose a file with a valid name.")
        if (bytes.isEmpty()) throw ChatApiException("The selected file is empty.")
        if (bytes.size > MAX_UPLOAD_BYTES) throw ChatApiException("File is too large. Maximum size is 2 MB.")
        val backendUrl = requireBackendUrl()
        val connection = openConnection("$backendUrl/api/files", requireAuthToken())
        return try {
            val payload = JSONObject().apply {
                put("filename", safeFilename)
                put("content_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }.toString()
            val body = executeJson(connection, payload)
            val json = JSONObject(body)
            val response = FileUploadResponse(
                fileId = json.optString("file_id"),
                filename = json.optString("filename"),
                bytes = json.optInt("bytes"),
                textPreview = json.optString("text_preview"),
            )
            if (response.fileId.isBlank()) throw ChatApiException("Sankalp backend returned no file id")
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun requireBackendUrl(): String {
        val backendUrl = BuildConfig.INDOONE_BACKEND_URL.trimEnd('/')
        if (backendUrl.isBlank()) throw ChatApiException("Sankalp backend URL is not configured")
        // Any real host is accepted, and only a malformed one is refused.
        //
        // This used to be a hardcoded list of three addresses. The backend is due to move, and
        // the day it does, every terminal build would have refused to talk to it with
        // "unsupported backend" - a rename-sized change turned into an outage by a list nobody
        // would remember was there.
        if (!backendUrl.startsWith("https://") && !backendUrl.startsWith("http://")) {
            throw ChatApiException("Sankalp backend URL must use http:// or https://")
        }
        return backendUrl
    }

    private fun requireAuthToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw ChatApiException("Please sign in to use Sankalp.")
        return try {
            Tasks.await(user.getIdToken(false)).token
                ?.takeIf { it.isNotBlank() }
                ?: throw ChatApiException("Could not get the Sankalp authentication token.")
        } catch (error: ChatApiException) {
            throw error
        } catch (error: Exception) {
            throw ChatApiException("Could not refresh your Sankalp authentication session. Please login again.", error)
        }
    }

    private fun warmUpBackend(backendUrl: String) {
        val now = System.currentTimeMillis()
        if (now - lastSuccessfulWarmupAt < WARMUP_COOLDOWN_MS) {
            return
        }

        val connection = try {
            (URL("$backendUrl/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = WARMUP_TIMEOUT_MS
                readTimeout = WARMUP_TIMEOUT_MS
                doInput = true
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-store")
            }
        } catch (_: IOException) {
            return
        }

        try {
            if (connection.responseCode in 200..299) {
                lastSuccessfulWarmupAt = System.currentTimeMillis()
            }
        } catch (_: IOException) {
            // Warm-up is best-effort; the chat request still reports the real error.
        } finally {
            connection.disconnect()
        }
    }

    // The same pipeline as /api/chat, read as it is produced.
    //
    // Deltas are handed to the caller on the Main thread, because they land in Compose
    // state. The socket stays on IO. A stream that ends without its final event is an
    // error, not a short answer: the caller falls back to the durable worker.
    suspend fun streamMessage(
        message: String,
        conversationId: String?,
        onMeta: suspend (String) -> Unit = {},
        onDelta: suspend (String) -> Unit = {},
    ): ChatResponse {
        val token = requireAuthToken()
        val backendUrl = requireBackendUrl()
        val payload = JSONObject().apply {
            put("message", message)
            if (!conversationId.isNullOrBlank()) put("conversation_id", conversationId)
        }.toString()

        return withContext(Dispatchers.IO) {
            val connection = openConnection("$backendUrl/api/chat/stream", token)
            connection.setRequestProperty("Accept", "text/event-stream")

            try {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode

                if (code !in 200..299) {
                    throw ChatApiException("Sankalp stream failed ($code)")
                }

                var conversation = conversationId.orEmpty()
                val text = StringBuilder()
                var final: ChatResponse? = null
                val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))

                reader.use { stream ->
                    while (true) {
                        val line = stream.readLine() ?: break

                        if (!line.startsWith("data: ")) {
                            continue
                        }

                        val event = runCatching {
                            JSONObject(line.removePrefix("data: "))
                        }.getOrNull() ?: continue

                        when (event.optString("type")) {
                            "meta" -> {
                                conversation = event.optString("conversation_id").ifBlank { conversation }
                                if (conversation.isNotBlank()) {
                                    withContext(Dispatchers.Main) { onMeta(conversation) }
                                }
                            }

                            "delta" -> {
                                val chunk = event.optString("text")

                                if (chunk.isNotEmpty()) {
                                    text.append(chunk)
                                    val soFar = text.toString()
                                    withContext(Dispatchers.Main) { onDelta(soFar) }
                                }
                            }

                            "done" -> {
                                val streamed = event.optString("reply").ifBlank { text.toString() }
                                val sources = parseSources(event.optJSONArray("sources"))
                                final = ChatResponse(
                                    conversationId = conversation,
                                    reply = withSources(streamed, sources),
                                    actionChips = parseActionChips(event.optJSONArray("action_chips")),
                                    sources = sources,
                                )
                            }
                        }
                    }
                }

                final ?: throw ChatApiException("Sankalp stream ended without a reply")
            } finally {
                connection.disconnect()
            }
        }
    }

    // A server-side feature flag may keep this list empty. Never turn chat chips
    // into agent approval requests; only official, already-paused runs appear.
    suspend fun listAgentApprovals(): List<AgentApproval> = withContext(Dispatchers.IO) {
        val json = approvalGet("/api/agent/approvals")
        val items = json.optJSONArray("approvals") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                parseAgentApproval(items.optJSONObject(index))?.let { add(it) }
            }
        }
    }

    suspend fun getAgentApproval(runId: String): AgentApproval = withContext(Dispatchers.IO) {
        parseAgentApproval(approvalGet(approvalPath(runId)))
            ?: throw ChatApiException("This approval is no longer available.")
    }

    suspend fun decideAgentApproval(runId: String, decision: String): AgentApprovalOutcome =
        withContext(Dispatchers.IO) {
            if (decision != "approve" && decision != "deny") {
                throw ChatApiException("Invalid approval decision.")
            }
            val connection = openConnection(
                requireBackendUrl() + approvalPath(runId),
                requireAuthToken(),
            ).apply {
                readTimeout = 35_000
            }
            try {
                // Exactly one POST. The provider may have received a write even
                // if this connection times out: NEVER retry this request.
                val body = executeJson(connection, JSONObject().put("decision", decision).toString())
                val result = JSONObject(body)
                AgentApprovalOutcome(
                    runId = result.optString("run_id"),
                    status = result.optString("status"),
                    requiresManualReview = result.optBoolean("requires_manual_review", false),
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun approvalPath(runId: String): String {
        val normalized = runId.trim()
        val uuid = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        if (!uuid.matches(normalized)) throw ChatApiException("Invalid agent run identifier.")
        return "/api/agent/runs/$normalized/approval"
    }

    private fun parseAgentApproval(json: JSONObject?): AgentApproval? {
        if (json == null || json.optString("status") != "pending") return null
        val id = json.optString("run_id")
        if (runCatching { approvalPath(id) }.isFailure) return null
        val tool = json.optString("tool").trim()
        val payload = json.optString("payload")
        val expiry = json.optString("expires_at")
        if (tool.isEmpty() || expiry.isBlank()) return null
        return AgentApproval(id, tool, payload, "pending", expiry)
    }

    private fun approvalGet(path: String): JSONObject {
        val connection = (URL(requireBackendUrl() + path).openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 15_000
                doInput = true
                doOutput = false
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer " + requireAuthToken())
            }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw ChatApiException(
                "Could not read agent approvals.", statusCode = code
            )
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } catch (error: IOException) {
            throw ChatApiException("Could not reach the approval service.", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, authToken: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            doInput = true
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $authToken")
        }

    private fun executeJson(connection: HttpURLConnection, payload: String): String {
        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (responseCode !in 200..299) {
                val errorMessage = runCatching {
                    val errorJson = JSONObject(body)
                    errorJson.optString("message").ifBlank {
                        errorJson.optString("detail")
                    }
                }.getOrDefault("").trim()

                val safeServerMessage = errorMessage
                    .takeIf { it.isNotBlank() && !it.contains("%02") && !it.contains("%0d") && !it.contains("%0D") }

                val retryAfterMs = connection.getHeaderField("Retry-After")
                    ?.trim()
                    ?.toLongOrNull()
                    ?.coerceIn(1L, 30L)
                    ?.times(1000L)

                // A 503 carries wording written for the user ("handling a lot of
                // requests"), so show that wording on its own instead of wrapping
                // it in an HTTP status line.
                val displayMessage = if (responseCode >= 500 && !safeServerMessage.isNullOrBlank()) {
                    safeServerMessage
                } else {
                    safeServerMessage?.let { "Sankalp backend returned HTTP $responseCode: $it" }
                        ?: "Sankalp backend returned HTTP $responseCode"
                }
                throw ChatApiException(
                    displayMessage,
                    statusCode = responseCode,
                    retryAfterMs = retryAfterMs,
                )
            }
            return body
        } catch (error: SocketTimeoutException) {
            throw ChatApiException("Sankalp is taking too long to respond. Please try again.", error)
        } catch (error: UnknownHostException) {
            throw ChatApiException("Sankalp backend could not be reached. Check your internet connection.", error)
        } catch (error: IOException) {
            throw ChatApiException(
                "Sankalp network error (${error.javaClass.simpleName}). Please check your internet connection and try again.",
                error,
            )
        }
    }

    /** Mint a read-only link to a thread, and return it. */
    suspend fun shareThread(conversationId: String): String = withContext(Dispatchers.IO) {
        val connection = openConnection(
            "${requireBackendUrl()}/api/chat/threads/$conversationId/share",
            requireAuthToken(),
        )

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write("{}".toByteArray()) }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw ChatApiException("Sankalp backend could not create a share link")
            }

            val url = JSONObject(body).optString("share_url").trim()

            if (url.isBlank()) throw ChatApiException("Sankalp backend returned no share link")

            url
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Tell the backend that a thread is gone.
     *
     * The backend keeps its own copy of every conversation, keyed by the same id, and it is that
     * copy that answers the next message. Deleting only the Firestore document left the backend
     * believing the thread was still open, so a client that sent the id again got the old context
     * back. This is the call that removes it there too.
     */
    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        val connection = openConnection(
            "${requireBackendUrl()}/api/chat/conversations/$conversationId",
            requireAuthToken(),
        )

        try {
            connection.requestMethod = "DELETE"
            val code = connection.responseCode

            // 404 is fine: the backend may never have stored this thread, and the caller's
            // delete has already succeeded where it mattered.
            if (code !in 200..299 && code != 404) {
                throw ChatApiException("Sankalp backend could not delete the conversation")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Rename a thread, and return the title as the backend stored it. */
    suspend fun renameThread(conversationId: String, title: String): String = withContext(Dispatchers.IO) {
        val connection = openConnection(
            "${requireBackendUrl()}/api/chat/threads/$conversationId",
            requireAuthToken(),
        )

        try {
            connection.requestMethod = "PATCH"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("title", title).toString()
            connection.outputStream.use { it.write(payload.toByteArray()) }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw ChatApiException("Sankalp backend could not rename this chat")
            }

            JSONObject(body).optString("status").trim().ifBlank { title.trim() }
        } finally {
            connection.disconnect()
        }
    }
}

class ChatApiException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null,
    val retryAfterMs: Long? = null,
) : Exception(message, cause)

/**
 * A failure as a sentence a person can read.
 *
 * The chat screen put `error.message` into a reply bubble, so a dead network appeared in the
 * conversation as "java.net.SocketTimeoutException: timeout" - Sankalp apparently saying the
 * name of an exception. Our own exceptions already carry a written sentence; everything else
 * gets one from here, and the detail goes to the log where it is useful.
 */
fun friendlyChatError(error: Throwable?): String = when (error) {
    null -> "Sankalp could not complete the request. Please try again."
    is ChatApiException -> error.message ?: "Sankalp could not complete the request. Please try again."
    is java.net.SocketTimeoutException ->
        "Sankalp took too long to answer. Check your connection and try again."
    is java.net.UnknownHostException ->
        "Sankalp could not reach the server. Check your connection and try again."
    is java.io.IOException ->
        "The connection dropped before the answer arrived. Please try again."
    is SecurityException -> "This account is not allowed to do that."
    else -> "Something went wrong. Please try again."
}
