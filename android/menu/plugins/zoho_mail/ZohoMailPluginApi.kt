package com.indoone.menu.plugins.zoho_mail

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.indoone.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ZohoMailConnectionStatus(
    val connected: Boolean,
    val scope: String = "",
    val permissionsComplete: Boolean = false,
    val missingScopes: List<String> = emptyList(),
    val reauthorizationRequired: Boolean = false,
    val updatedAt: String? = null,
)

data class ZohoMailAccount(
    val id: String,
    val emailAddress: String,
    val displayName: String,
)

data class ZohoMailFolder(
    val id: String,
    val name: String,
    val folderType: String,
)

data class ZohoMailMessage(
    val id: String,
    val folderId: String,
    val subject: String,
    val fromAddress: String,
    val summary: String,
    val receivedTime: String,
)

data class ZohoMailMessageContent(
    val subject: String,
    val fromAddress: String,
    val toAddress: String,
    val content: String,
)

object ZohoMailPluginApi {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 25_000

    suspend fun createAuthorizationUrl(): String = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val userId = requireUserId()
        val authToken = requireAuthToken()
        val payload = JSONObject().put("user_id", userId).toString()
        val connection = openConnection(
            url = "${backendUrl}/api/integrations/zoho-mail/start",
            method = "POST",
            authToken = authToken,
        )
        try {
            val body = execute(connection, payload)
            JSONObject(body).optString("authorization_url").trim()
                .takeIf { it.startsWith("https://") }
                ?: throw ZohoMailPluginException("The mail service returned no valid authorization URL.")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun getConnectionStatus(): ZohoMailConnectionStatus = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val userId = requireUserId()
        val authToken = requireAuthToken()
        val encodedUserId = URLEncoder.encode(userId, "UTF-8")
        val connection = openConnection(
            url = "${backendUrl}/api/integrations/zoho-mail/connection?user_id=$encodedUserId",
            method = "GET",
            authToken = authToken,
        )
        try {
            val json = JSONObject(execute(connection, null))
            val missingScopesJson = json.optJSONArray("missing_scopes")
            val missingScopes = if (missingScopesJson == null) {
                emptyList()
            } else {
                (0 until missingScopesJson.length()).mapNotNull { index ->
                    missingScopesJson.optString(index).trim().takeIf { it.isNotBlank() }
                }
            }
            val connected = json.optBoolean("connected", false)
            if (connected) {
                saveProfileConnection()
            }
            ZohoMailConnectionStatus(
                connected = connected,
                scope = json.optString("scope", ""),
                permissionsComplete = json.optBoolean("permissions_complete", false),
                missingScopes = missingScopes,
                reauthorizationRequired = json.optBoolean("reauthorization_required", false),
                updatedAt = json.optString("updated_at").takeIf { it.isNotBlank() },
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun listAccounts(): List<ZohoMailAccount> {
        val json = postJson(
            "/api/integrations/zoho-mail/accounts",
            JSONObject().put("user_id", requireUserId()),
        )
        return jsonDataArray(json).mapNotNull { item ->
            val accountId = item.optString("accountId").trim()
            if (accountId.isBlank()) return@mapNotNull null
            val primaryEmail = item.optString("primaryEmailAddress").trim()
                .ifBlank { item.optString("mailboxAddress").trim() }
                .ifBlank { item.optString("incomingUserName").trim() }
                .ifBlank { firstEmailAddress(item.optJSONArray("emailAddress")) }
            ZohoMailAccount(
                id = accountId,
                emailAddress = primaryEmail.ifBlank { "Zoho Mail account" },
                displayName = item.optString("accountDisplayName").trim()
                    .ifBlank { item.optString("displayName").trim() }
                    .ifBlank { primaryEmail.ifBlank { "Zoho Mail account" } },
            )
        }
    }

    suspend fun listFolders(accountId: String): List<ZohoMailFolder> {
        val json = postJson(
            "/api/integrations/zoho-mail/folders",
            JSONObject().put("user_id", requireUserId()).put("account_id", accountId),
        )
        return jsonDataArray(json).mapNotNull { item ->
            val folderId = item.optString("folderId").trim()
            if (folderId.isBlank()) return@mapNotNull null
            ZohoMailFolder(
                id = folderId,
                name = item.optString("folderName").trim()
                    .ifBlank { item.optString("path").trim().trim('/') }
                    .ifBlank { "Folder" },
                folderType = item.optString("folderType").trim(),
            )
        }
    }

    suspend fun listMessages(
        accountId: String,
        folderId: String,
        searchKey: String = "",
        start: Int = 1,
        limit: Int = 30,
    ): List<ZohoMailMessage> {
        val payload = JSONObject()
            .put("user_id", requireUserId())
            .put("account_id", accountId)
            .put("folder_id", folderId)
            .put("search_key", searchKey.trim())
            .put("start", start)
            .put("limit", limit)
        val json = postJson("/api/integrations/zoho-mail/messages", payload)
        return jsonDataArray(json).mapNotNull { item ->
            val messageId = item.optString("messageId").trim()
            if (messageId.isBlank()) return@mapNotNull null
            ZohoMailMessage(
                id = messageId,
                folderId = item.optString("folderId").trim().ifBlank { folderId },
                subject = item.optString("subject").trim().ifBlank { "(No subject)" },
                fromAddress = item.optString("fromAddress").trim()
                    .ifBlank { item.optString("sender").trim() },
                summary = item.optString("summary").trim(),
                receivedTime = item.optString("receivedTime").trim(),
            )
        }
    }

    suspend fun readMessage(
        accountId: String,
        folderId: String,
        messageId: String,
    ): ZohoMailMessageContent {
        val json = postJson(
            "/api/integrations/zoho-mail/message",
            JSONObject()
                .put("user_id", requireUserId())
                .put("account_id", accountId)
                .put("folder_id", folderId)
                .put("message_id", messageId),
        )
        val data = json.optJSONObject("data") ?: json
        return ZohoMailMessageContent(
            subject = data.optString("subject").trim(),
            fromAddress = data.optString("fromAddress").trim(),
            toAddress = data.optString("toAddress").trim(),
            content = data.optString("content").trim()
                .ifBlank { data.optString("htmlContent").trim() }
                .ifBlank { data.optString("summary").trim() },
        )
    }

    suspend fun sendMessage(
        accountId: String,
        fromAddress: String,
        toAddress: String,
        subject: String,
        content: String,
        approved: Boolean = false,
    ) {
        postJson(
            "/api/integrations/zoho-mail/send",
            JSONObject()
                .put("user_id", requireUserId())
                .put("account_id", accountId)
                .put("from_address", fromAddress.trim())
                .put("to_address", toAddress.trim())
                .put("subject", subject.trim())
                .put("content", content)
                .put("approved", approved),
        )
    }

    suspend fun replyToMessage(
        accountId: String,
        messageId: String,
        fromAddress: String,
        toAddress: String,
        subject: String,
        content: String,
        approved: Boolean = false,
    ) {
        postJson(
            "/api/integrations/zoho-mail/reply",
            JSONObject()
                .put("user_id", requireUserId())
                .put("account_id", accountId)
                .put("message_id", messageId)
                .put("from_address", fromAddress.trim())
                .put("to_address", toAddress.trim())
                .put("subject", subject.trim())
                .put("content", content)
                .put("approved", approved),
        )
    }

    suspend fun disconnect() {
        val userId = requireUserId()
        postJson(
            "/api/integrations/zoho-mail/disconnect",
            JSONObject().put("user_id", userId),
        )
        withContext(Dispatchers.IO) {
            Tasks.await(zohoMailConnectionReference(userId).removeValue())
        }
    }

    private suspend fun saveProfileConnection() = withContext(Dispatchers.IO) {
        Tasks.await(zohoMailConnectionReference(requireUserId()).setValue("Zoho Mail"))
    }

    private fun zohoMailConnectionReference(userId: String) =
        FirebaseDatabase.getInstance().reference
            .child("users")
            .child(userId)
            .child("profile")
            .child("plugins")
            .child("zoho_mail")

    private suspend fun postJson(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val connection = openConnection(
            url = "$backendUrl$path",
            method = "POST",
            authToken = requireAuthToken(),
        )
        try {
            JSONObject(execute(connection, payload.toString()))
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonDataArray(json: JSONObject): List<JSONObject> {
        val array = json.optJSONArray("data")
            ?: json.optJSONObject("data")?.let { data ->
                data.optJSONArray("data")
                    ?: data.optJSONArray("accounts")
                    ?: data.optJSONArray("folders")
                    ?: data.optJSONArray("messages")
            }
            ?: json.optJSONArray("accounts")
            ?: json.optJSONArray("folders")
            ?: json.optJSONArray("messages")
            ?: JSONArray()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun firstEmailAddress(addresses: JSONArray?): String {
        if (addresses == null) return ""
        for (index in 0 until addresses.length()) {
            val item = addresses.optJSONObject(index) ?: continue
            val email = item.optString("mailId").trim()
            if (email.isNotBlank() && (item.optBoolean("isPrimary", false) || index == 0)) {
                return email
            }
        }
        return ""
    }

    private fun requireBackendUrl(): String {
        val value = BuildConfig.INDOONE_BACKEND_URL.trimEnd('/')
        if (value.isBlank()) throw ZohoMailPluginException("Sankalp backend URL is not configured.")
        return value
    }

    private fun requireUserId(): String =
        FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw ZohoMailPluginException("Please sign in to Sankalp first.")

    private fun requireAuthToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw ZohoMailPluginException("Please sign in to Sankalp first.")
        return try {
            Tasks.await(user.getIdToken(false)).token
                ?.takeIf { it.isNotBlank() }
                ?: throw ZohoMailPluginException("Could not get your Sankalp authentication token.")
        } catch (error: ZohoMailPluginException) {
            throw error
        } catch (error: Exception) {
            throw ZohoMailPluginException("Could not refresh your Sankalp authentication session.", error)
        }
    }

    private fun openConnection(
        url: String,
        method: String,
        authToken: String,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            doOutput = method == "POST"
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $authToken")
            if (method == "POST") {
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }

    private fun execute(connection: HttpURLConnection, payload: String?): String {
        try {
            if (payload != null) {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (responseCode !in 200..299) {
                val message = runCatching {
                    val errorJson = JSONObject(body)
                    errorJson.optString("message").ifBlank { errorJson.optString("detail") }
                }.getOrDefault("").trim()
                throw ZohoMailPluginException(
                    message.takeIf { it.isNotBlank() }
                        ?: "Sankalp backend returned HTTP $responseCode.",
                )
            }
            return body
        } catch (error: ZohoMailPluginException) {
            throw error
        } catch (error: Exception) {
            throw ZohoMailPluginException(
                "Could not reach Sankalp backend. Check your internet connection.",
                error,
            )
        }
    }
}

class ZohoMailPluginException(message: String, cause: Throwable? = null) : Exception(message, cause)
