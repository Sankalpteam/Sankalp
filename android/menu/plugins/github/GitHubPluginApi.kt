package com.indoone.menu.plugins.github

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

data class GitHubConnectResult(val authorizationUrl: String)
data class GitHubConnectionStatus(
    val connected: Boolean,
    val login: String = "",
    val providerHealthy: Boolean = false,
    val updatedAt: String = "",
)
data class GitHubRepository(
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val htmlUrl: String,
)

data class GitHubIssue(
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val repositoryUrl: String,
    val labels: List<String>,
)

data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val draft: Boolean,
)

data class GitHubRepositoryEntry(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val htmlUrl: String,
)

data class GitHubRepositoryFileContent(
    val path: String,
    val sha: String,
    val htmlUrl: String,
    val content: String,
    val truncated: Boolean,
)

data class GitHubCodeSearchMatch(
    val name: String,
    val path: String,
    val htmlUrl: String,
    val snippets: List<String>,
)

data class GitHubRepositoryCommit(
    val sha: String,
    val message: String,
    val author: String,
    val date: String,
    val htmlUrl: String,
)

data class GitHubCommitFileChange(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val htmlUrl: String,
)

data class GitHubCommitComparison(
    val repository: String,
    val base: String,
    val head: String,
    val status: String,
    val aheadBy: Int,
    val behindBy: Int,
    val totalCommits: Int,
    val files: List<GitHubCommitFileChange>,
)

data class GitHubFileCommitResult(
    val path: String,
    val fileSha: String,
    val fileUrl: String,
    val commitSha: String,
    val commitUrl: String,
    val commitMessage: String,
)

object GitHubPluginApi {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val CALLBACK_PATH = "/api/integrations/github/callback"

    suspend fun startConnection(): GitHubConnectResult = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val payload = JSONObject().apply {
            put("user_id", requireUserId())
            put("redirect_uri", backendUrl + CALLBACK_PATH)
        }
        val result = request(
            "POST",
            "${backendUrl}/api/integrations/github/connect",
            payload,
        )
        val authorizationUrl = result.optString("authorization_url").trim()
        if (authorizationUrl.isBlank()) {
            throw GitHubPluginException("No authorization URL was returned. Please try again.")
        }
        GitHubConnectResult(authorizationUrl)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val userId = requireUserId()
        request(
            "POST",
            "${backendUrl}/api/integrations/github/disconnect",
            JSONObject().put("user_id", userId),
        )
        Tasks.await(githubConnectionReference(userId).removeValue())
    }

    suspend fun getConnectionStatus(): GitHubConnectionStatus = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val userId = requireUserId()
        val status = request(
            "GET",
            "${backendUrl}/api/integrations/github/status?user_id=${URLEncoder.encode(userId, "UTF-8")}",
        )
        val connected = status.optBoolean("connected", false)
        val updatedAt = status.optJSONObject("metadata")?.optString("updated_at").orEmpty()
        if (!connected) return@withContext GitHubConnectionStatus(connected = false, updatedAt = updatedAt)

        val probe = runCatching {
            request(
                "POST",
                "${backendUrl}/api/integrations/github/probe",
                JSONObject().put("user_id", userId),
            )
        }.getOrNull()
        val login = probe?.optJSONObject("summary")?.optString("login").orEmpty()
        saveProfileConnection()
        GitHubConnectionStatus(
            connected = true,
            login = login,
            providerHealthy = probe?.optBoolean("provider_ok", false) == true,
            updatedAt = updatedAt,
        )
    }

    suspend fun listRepositories(page: Int = 1, perPage: Int = 10): List<GitHubRepository> =
        withContext(Dispatchers.IO) {
            val backendUrl = requireBackendUrl()
            val result = request(
                "POST",
                "${backendUrl}/api/integrations/github/repositories",
                JSONObject().apply {
                    put("user_id", requireUserId())
                    put("page", page)
                    put("per_page", perPage.coerceIn(1, 100))
                },
            )
            val items: JSONArray = result.optJSONArray("repositories") ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    add(
                        GitHubRepository(
                            fullName = item.optString("full_name").trim(),
                            isPrivate = item.optBoolean("private", false),
                            defaultBranch = item.optString("default_branch").trim(),
                            htmlUrl = item.optString("html_url").trim(),
                        ),
                    )
                }
            }
        }


    suspend fun listAssignedIssues(page: Int = 1, perPage: Int = 10): List<GitHubIssue> =
        withContext(Dispatchers.IO) {
            val backendUrl = requireBackendUrl()
            val result = request(
                "POST",
                "${backendUrl}/api/integrations/github/issues",
                JSONObject().apply {
                    put("user_id", requireUserId())
                    put("page", page)
                    put("per_page", perPage.coerceIn(1, 100))
                },
            )
            val items: JSONArray = result.optJSONArray("issues") ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val labelsJson = item.optJSONArray("labels") ?: JSONArray()
                    val labels = buildList {
                        for (labelIndex in 0 until labelsJson.length()) {
                            labelsJson.optString(labelIndex).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    add(
                        GitHubIssue(
                            number = item.optInt("number"),
                            title = item.optString("title").trim(),
                            state = item.optString("state").trim(),
                            htmlUrl = item.optString("html_url").trim(),
                            repositoryUrl = item.optString("repository_url").trim(),
                            labels = labels,
                        ),
                    )
                }
            }
        }

    suspend fun listPullRequests(
        repository: String,
        page: Int = 1,
        perPage: Int = 10,
    ): List<GitHubPullRequest> = withContext(Dispatchers.IO) {
        val normalizedRepository = repository.trim()
        if (normalizedRepository.isBlank()) {
            throw GitHubPluginException("Choose a repository to load its pull requests.")
        }
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/integrations/github/pull-requests",
            JSONObject().apply {
                put("user_id", requireUserId())
                put("repository", normalizedRepository)
                put("page", page)
                put("per_page", perPage.coerceIn(1, 100))
            },
        )
        val items: JSONArray = result.optJSONArray("pull_requests") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    GitHubPullRequest(
                        number = item.optInt("number"),
                        title = item.optString("title").trim(),
                        state = item.optString("state").trim(),
                        htmlUrl = item.optString("html_url").trim(),
                        draft = item.optBoolean("draft", false),
                    ),
                )
            }
        }
    }

    suspend fun listRepositoryContents(
        repository: String,
        path: String = "",
        ref: String = "",
    ): List<GitHubRepositoryEntry> = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/integrations/github/contents",
            JSONObject().apply {
                put("user_id", requireUserId())
                put("repository", repository.trim())
                put("path", path.trim())
                put("ref", ref.trim())
            },
        )
        val items = result.optJSONArray("contents") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    GitHubRepositoryEntry(
                        name = item.optString("name").trim(),
                        path = item.optString("path").trim(),
                        type = item.optString("type").trim(),
                        size = item.optLong("size", 0L).coerceAtLeast(0L),
                        htmlUrl = item.optString("html_url").trim(),
                    ),
                )
            }
        }
    }

    suspend fun readRepositoryFile(
        repository: String,
        path: String,
        ref: String = "",
    ): GitHubRepositoryFileContent = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/integrations/github/read-file",
            JSONObject().apply {
                put("user_id", requireUserId())
                put("repository", repository.trim())
                put("path", path.trim())
                put("ref", ref.trim())
            },
        )
        GitHubRepositoryFileContent(
            path = result.optString("path").trim(),
            sha = result.optString("sha").trim(),
            htmlUrl = result.optString("html_url").trim(),
            content = result.optString("content"),
            truncated = result.optBoolean("truncated", false),
        )
    }

    suspend fun requestRepositoryFileCommitApproval(): String = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/approvals/issue",
            JSONObject().put("tool", "github_update_repository_file").put("ttl_seconds", 180),
        )
        result.optString("approval_token").trim().takeIf { it.isNotBlank() }
            ?: throw GitHubPluginException("Sankalp did not issue a file-commit approval token.")
    }

    suspend fun commitRepositoryFile(
        repository: String,
        path: String,
        content: String,
        message: String,
        expectedSha: String,
        approvalToken: String,
        branch: String = "",
        create: Boolean = false,
    ): GitHubFileCommitResult = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/integrations/github/commit-file",
            JSONObject().apply {
                put("user_id", requireUserId())
                put("repository", repository.trim())
                put("path", path.trim())
                put("content", content)
                put("message", message.trim())
                put("sha", expectedSha.trim())
                put("create", create)
                put("approval_token", approvalToken.trim())
                put("branch", branch.trim())
            },
        )
        GitHubFileCommitResult(
            path = result.optString("path").trim(),
            fileSha = result.optString("file_sha").trim(),
            fileUrl = result.optString("file_url").trim(),
            commitSha = result.optString("commit_sha").trim(),
            commitUrl = result.optString("commit_url").trim(),
            commitMessage = result.optString("commit_message").trim(),
        )
    }

    suspend fun suggestRepositoryFileEdit(
        repository: String,
        path: String,
        instruction: String,
        currentContent: String,
    ): String = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/code-edit-suggestion",
            JSONObject().apply {
                put("repository", repository.trim())
                put("path", path.trim())
                put("instruction", instruction.trim())
                put("content", currentContent)
            },
        )
        result.optString("content").takeIf { it.isNotBlank() }
            ?: throw GitHubPluginException("Sankalp returned no complete file proposal.")
    }

    suspend fun searchRepositoryCode(
        repository: String,
        query: String,
        perPage: Int = 10,
    ): List<GitHubCodeSearchMatch> = withContext(Dispatchers.IO) {
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/integrations/github/search-code",
            JSONObject().apply {
                put("user_id", requireUserId())
                put("repository", repository.trim())
                put("query", query.trim())
                put("per_page", perPage.coerceIn(1, 30))
            },
        )
        val items = result.optJSONArray("results") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val snippetsJson = item.optJSONArray("snippets") ?: JSONArray()
                val snippets = buildList {
                    for (snippetIndex in 0 until snippetsJson.length()) {
                        val snippet = snippetsJson.optString(snippetIndex)
                        if (snippet.isNotBlank()) add(snippet)
                    }
                }
                add(
                    GitHubCodeSearchMatch(
                        name = item.optString("name").trim(),
                        path = item.optString("path").trim(),
                        htmlUrl = item.optString("html_url").trim(),
                        snippets = snippets,
                    ),
                )
            }
        }
    }

    suspend fun listRepositoryCommits(
        repository: String,
        path: String = "",
        perPage: Int = 10,
    ): List<GitHubRepositoryCommit> = withContext(Dispatchers.IO) {
        if (repository.isBlank()) {
            throw GitHubPluginException("Choose a repository to load its commit history.")
        }
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/integrations/github/commits",
            JSONObject().apply {
                put("user_id", requireUserId())
                put("repository", repository.trim())
                put("path", path.trim())
                put("per_page", perPage.coerceIn(1, 30))
            },
        )
        val items = result.optJSONArray("commits") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    GitHubRepositoryCommit(
                        sha = item.optString("sha").trim(),
                        message = item.optString("message").trim(),
                        author = item.optString("author").trim(),
                        date = item.optString("date").trim(),
                        htmlUrl = item.optString("html_url").trim(),
                    ),
                )
            }
        }
    }

    suspend fun compareRepositoryCommits(
        repository: String,
        base: String,
        head: String,
    ): GitHubCommitComparison = withContext(Dispatchers.IO) {
        if (repository.isBlank()) {
            throw GitHubPluginException("Choose a repository before comparing commits.")
        }
        if (base.isBlank() || head.isBlank()) {
            throw GitHubPluginException("Enter both the base and comparison branch or commit.")
        }
        val backendUrl = requireBackendUrl()
        val result = request(
            "POST",
            "${backendUrl}/api/integrations/github/compare",
            JSONObject().apply {
                put("user_id", requireUserId())
                put("repository", repository.trim())
                put("base", base.trim())
                put("head", head.trim())
            },
        )
        val fileItems = result.optJSONArray("files") ?: JSONArray()
        val changedFiles = buildList {
            for (index in 0 until fileItems.length()) {
                val item = fileItems.optJSONObject(index) ?: continue
                add(
                    GitHubCommitFileChange(
                        filename = item.optString("filename").trim(),
                        status = item.optString("status").trim(),
                        additions = item.optInt("additions"),
                        deletions = item.optInt("deletions"),
                        changes = item.optInt("changes"),
                        htmlUrl = item.optString("html_url").trim(),
                    ),
                )
            }
        }
        GitHubCommitComparison(
            repository = result.optString("repository").ifBlank { repository.trim() },
            base = result.optString("base").ifBlank { base.trim() },
            head = result.optString("head").ifBlank { head.trim() },
            status = result.optString("status").ifBlank { "unknown" },
            aheadBy = result.optInt("ahead_by"),
            behindBy = result.optInt("behind_by"),
            totalCommits = result.optInt("total_commits"),
            files = changedFiles,
        )
    }

    private suspend fun saveProfileConnection() = withContext(Dispatchers.IO) {
        Tasks.await(githubConnectionReference(requireUserId()).setValue("GitHub"))
    }

    private fun githubConnectionReference(userId: String) =
        FirebaseDatabase.getInstance().reference
            .child("users")
            .child(userId)
            .child("profile")
            .child("plugins")
            .child("github")

    private fun requireBackendUrl(): String =
        BuildConfig.INDOONE_BACKEND_URL.trimEnd('/').takeIf { it.isNotBlank() }
            ?: throw GitHubPluginException("Sankalp backend URL is not configured.")

    private fun requireUserId(): String =
        FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw GitHubPluginException("Please sign in to Sankalp first.")

    private fun requireAuthToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw GitHubPluginException("Please sign in to Sankalp first.")
        return try {
            Tasks.await(user.getIdToken(false)).token
                ?.takeIf { it.isNotBlank() }
                ?: throw GitHubPluginException("Could not get your Sankalp authentication token.")
        } catch (error: GitHubPluginException) {
            throw error
        } catch (error: Exception) {
            throw GitHubPluginException("Could not refresh your Sankalp authentication session.", error)
        }
    }

    private fun request(method: String, url: String, payload: JSONObject? = null): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            doOutput = payload != null
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${requireAuthToken()}")
            if (payload != null) setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            if (payload != null) {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optString("detail").ifBlank { JSONObject(body).optString("message") }
                }.getOrDefault("").trim()
                throw GitHubPluginException(
                    message.takeIf { it.isNotBlank() } ?: "Sankalp backend returned HTTP $code.",
                )
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        } catch (error: GitHubPluginException) {
            throw error
        } catch (error: Exception) {
            throw GitHubPluginException("Could not reach the Sankalp backend. Check your connection.", error)
        } finally {
            connection.disconnect()
        }
    }
}

class GitHubPluginException(message: String, cause: Throwable? = null) : Exception(message, cause)
