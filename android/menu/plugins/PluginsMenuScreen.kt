package com.indoone.menu.plugins

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.indoone.R
import com.indoone.menu.plugins.email.gmail.GmailPluginApi
import com.indoone.menu.plugins.github.GitHubConnectionStatus
import com.indoone.menu.plugins.github.GitHubPluginApi
import com.indoone.menu.plugins.zoho_mail.ZohoMailPluginApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AppPurple = Color(0xFF5E2DD2)
private val Ink = Color(0xFF272238)
private val Muted = Color(0xFF777286)
private val PalePurple = Color(0xFFF4F0FF)
private val ConnectedGreen = Color(0xFF218653)

@Composable
fun PluginsMenuScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var checking by remember { mutableStateOf(true) }
    var gmailConnected by remember { mutableStateOf<Boolean?>(null) }
    var gmailAddress by remember { mutableStateOf("") }
    var gmailConnecting by remember { mutableStateOf(false) }
    var zohoConnected by remember { mutableStateOf<Boolean?>(null) }
    var zohoAddress by remember { mutableStateOf("") }
    var zohoUpdatedAt by remember { mutableStateOf<String?>(null) }
    var zohoAuthorizing by remember { mutableStateOf(false) }
    var githubStatus by remember { mutableStateOf<GitHubConnectionStatus?>(null) }
    var githubBusy by remember { mutableStateOf(false) }
    var githubStatusMessage by remember { mutableStateOf("Checking GitHub connection…") }
    var pendingDisconnect by remember { mutableStateOf<String?>(null) }
    var disconnectingProvider by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        checking = true
        runCatching { GmailPluginApi.getConnectionStatus() }
            .onSuccess { result ->
                gmailConnected = result.connected
                gmailAddress = result.emailAddress.orEmpty()
            }
            .onFailure {
                gmailConnected = null
                statusMessage = "Could not check the Gmail connection. Try again."
            }

        runCatching { ZohoMailPluginApi.getConnectionStatus() }
            .onSuccess { result ->
                zohoConnected = result.connected
                zohoUpdatedAt = result.updatedAt
                if (result.connected) {
                    runCatching { ZohoMailPluginApi.listAccounts().firstOrNull()?.emailAddress.orEmpty() }
                        .onSuccess { zohoAddress = it }
                        .onFailure { zohoAddress = "" }
                } else {
                    zohoAddress = ""
                }
            }
            .onFailure {
                zohoConnected = null
                if (statusMessage.isBlank()) {
                    statusMessage = "Could not check the Zoho Mail connection. Try again."
                }
            }
        runCatching { GitHubPluginApi.getConnectionStatus() }
            .onSuccess { result ->
                githubStatus = result
                githubStatusMessage = when {
                    !result.connected -> "GitHub is not connected yet."
                    result.providerHealthy -> "GitHub connected and verified" +
                        result.login.takeIf { it.isNotBlank() }?.let { " as $it." }.orEmpty()
                    else -> "GitHub is connected, but API verification failed. Reauthorize to restore access."
                }
            }
            .onFailure {
                githubStatus = null
                githubStatusMessage = "Could not check GitHub connection. You can retry with +."
            }
        checking = false
    }

    fun startZohoAuthorization() {
        if (checking || zohoAuthorizing || disconnectingProvider == "zoho-mail") return
        scope.launch {
            val wasConnected = zohoConnected == true
            val previousUpdatedAt = zohoUpdatedAt
            zohoAuthorizing = true
            statusMessage = if (wasConnected) {
                "Opening Zoho Mail reauthorization…"
            } else {
                "Opening Zoho Mail authorization…"
            }
            try {
                val authorizationUrl = ZohoMailPluginApi.createAuthorizationUrl()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)))
                statusMessage = "Approve Sankalp in Zoho Mail. Waiting for connection verification…"
                var confirmed = false
                var latestConnected = false
                for (attempt in 0 until 60) {
                    delay(2_000L)
                    val latest = runCatching { ZohoMailPluginApi.getConnectionStatus() }.getOrNull() ?: continue
                    zohoConnected = latest.connected
                    zohoUpdatedAt = latest.updatedAt
                    latestConnected = latest.connected
                    val authorizationChanged = !wasConnected ||
                        (latest.updatedAt != null && latest.updatedAt != previousUpdatedAt)
                    if (latest.connected && latest.permissionsComplete &&
                        !latest.reauthorizationRequired && authorizationChanged
                    ) {
                        val accounts = runCatching { ZohoMailPluginApi.listAccounts() }.getOrNull().orEmpty()
                        if (accounts.isNotEmpty()) {
                            zohoAddress = accounts.firstOrNull()?.emailAddress.orEmpty()
                            confirmed = true
                            break
                        }
                    }
                }
                statusMessage = when {
                    confirmed -> "Zoho Mail connected and verified."
                    latestConnected -> "Zoho Mail is connected, but mailbox access needs verification. Reauthorize to restore access."
                    else -> "Zoho Mail authorization was not confirmed. Finish approval in the browser, then tap + to retry."
                }
            } catch (error: Exception) {
                statusMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not start Zoho Mail authorization. Please retry."
            } finally {
                zohoAuthorizing = false
            }
        }
    }

    fun startGitHubAuthorization() {
        if (checking || githubBusy || disconnectingProvider == "github") return
        scope.launch {
            val previous = githubStatus
            githubBusy = true
            githubStatusMessage = if (previous?.connected == true) {
                "Opening GitHub reauthorization…"
            } else {
                "Opening GitHub authorization…"
            }
            try {
                val result = GitHubPluginApi.startConnection()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.authorizationUrl)))
                githubStatusMessage = "Approve Sankalp in GitHub. Waiting for connection verification…"
                var confirmed: GitHubConnectionStatus? = null
                for (attempt in 0 until 60) {
                    delay(2_000L)
                    val latest = runCatching { GitHubPluginApi.getConnectionStatus() }.getOrNull() ?: continue
                    githubStatus = latest
                    val authorizationChanged = previous?.connected != true ||
                        (latest.updatedAt.isNotBlank() && latest.updatedAt != previous?.updatedAt)
                    if (latest.connected && latest.providerHealthy && authorizationChanged) {
                        confirmed = latest
                        break
                    }
                }
                githubStatusMessage = confirmed?.let { connection ->
                    "GitHub connected and verified" +
                        connection.login.takeIf { it.isNotBlank() }?.let { " as $it." }.orEmpty()
                } ?: when {
                    githubStatus?.connected == true ->
                        "GitHub authorization is saved, but access could not be verified. Reauthorize to restore access."
                    else ->
                        "GitHub authorization was not confirmed. Finish approval in the browser, then tap + to retry."
                }
            } catch (error: Exception) {
                githubStatusMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not start GitHub authorization. Please retry."
            } finally {
                githubBusy = false
            }
        }
    }

    fun finishGmailAuthorization(result: AuthorizationResult) {
        val serverAuthCode = result.serverAuthCode?.trim().orEmpty()
        if (serverAuthCode.isBlank()) {
            gmailConnecting = false
            statusMessage = "Google did not return a server authorization code. Reconnect Gmail and approve offline access."
            return
        }
        val grantedScope = result.grantedScopes.orEmpty().joinToString(" ")
        scope.launch {
            runCatching {
                GmailPluginApi.storeServerAuthCode(
                    serverAuthCode = serverAuthCode,
                    grantedScope = grantedScope,
                )
            }.onSuccess { saved ->
                gmailConnected = saved.connected
                gmailAddress = saved.emailAddress.orEmpty()
                statusMessage = if (saved.connected) {
                    "Gmail connected and verified" +
                        saved.emailAddress?.takeIf { it.isNotBlank() }?.let { " as $it." }.orEmpty()
                } else {
                    "Gmail authorization completed, but the connection was not saved everywhere."
                }
            }.onFailure { error ->
                statusMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not save Gmail authorization."
            }
            gmailConnecting = false
        }
    }

    // Google answers through an intent sender rather than a browser, so Gmail needs
    // its own launcher. The row's + starts it directly, with no page in between,
    // exactly like Zoho Mail and GitHub.
    val gmailAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        try {
            val result = Identity
                .getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(activityResult.data)
            finishGmailAuthorization(result)
        } catch (error: ApiException) {
            gmailConnecting = false
            statusMessage = "Google authorization failed (${error.statusCode}): " +
                (error.message ?: "unknown Google authorization error.")
        } catch (error: Exception) {
            gmailConnecting = false
            statusMessage = error.message ?: "Google authorization failed."
        }
    }

    fun startGmailAuthorization() {
        if (checking || gmailConnecting || disconnectingProvider == "gmail") return
        gmailConnecting = true
        statusMessage = if (gmailConnected == true) {
            "Opening Gmail reauthorization…"
        } else {
            "Opening Gmail authorization…"
        }
        scope.launch {
            try {
                val serverClientId = GmailPluginApi.getGoogleServerClientId()
                val authorizationRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(GmailPluginApi.GMAIL_SCOPE)))
                    .requestOfflineAccess(serverClientId)
                    .setPrompt(AuthorizationRequest.Prompt.CONSENT)
                    .build()

                Identity.getAuthorizationClient(context)
                    .authorize(authorizationRequest)
                    .addOnSuccessListener { authorizationResult ->
                        if (authorizationResult.hasResolution()) {
                            val pendingIntent = authorizationResult.pendingIntent
                            if (pendingIntent == null) {
                                gmailConnecting = false
                                statusMessage = "Google authorization could not start."
                            } else {
                                gmailAuthorizationLauncher.launch(
                                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                )
                            }
                        } else {
                            finishGmailAuthorization(authorizationResult)
                        }
                    }
                    .addOnFailureListener { error ->
                        gmailConnecting = false
                        statusMessage = error.message ?: "Could not start Google authorization."
                    }
            } catch (error: Exception) {
                gmailConnecting = false
                statusMessage = error.message ?: "Could not prepare secure Gmail authorization."
            }
        }
    }

    fun disconnect(provider: String) {
        if (disconnectingProvider != null) return
        scope.launch {
            disconnectingProvider = provider
            statusMessage = ""
            try {
                when (provider) {
                    "gmail" -> {
                        GmailPluginApi.disconnect()
                        gmailConnected = false
                        gmailAddress = ""
                    }
                    "zoho-mail" -> {
                        ZohoMailPluginApi.disconnect()
                        zohoConnected = false
                        zohoUpdatedAt = null
                        zohoAddress = ""
                    }
                    "github" -> {
                        GitHubPluginApi.disconnect()
                        githubStatus = GitHubConnectionStatus(connected = false)
                        githubStatusMessage = "GitHub disconnected."
                    }
                }
                statusMessage = when (provider) {
                    "gmail" -> "Gmail disconnected."
                    "zoho-mail" -> "Zoho Mail disconnected."
                    else -> "GitHub disconnected."
                }
            } catch (error: Exception) {
                statusMessage = error.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "Could not disconnect this account. Please try again."
            } finally {
                disconnectingProvider = null
                pendingDisconnect = null
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Text(
                    text = "Plugins",
                    color = Ink,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            MailProviderRow(
                title = "Gmail",
                accountAddress = gmailAddress,
                connected = gmailConnected,
                logoRes = R.drawable.gmail_logo,
                busy = checking || gmailConnecting || disconnectingProvider == "gmail",
                onAdd = { startGmailAuthorization() },
                onReauthorize = { startGmailAuthorization() },
                onDisconnect = { pendingDisconnect = "gmail" },
            )

            MailProviderRow(
                title = "Zoho Mail",
                accountAddress = zohoAddress,
                connected = zohoConnected,
                logoRes = R.drawable.zoho_mail_logo,
                busy = checking || zohoAuthorizing || disconnectingProvider == "zoho-mail",
                onAdd = { startZohoAuthorization() },
                onReauthorize = { startZohoAuthorization() },
                onDisconnect = { pendingDisconnect = "zoho-mail" },
            )

            GitHubProviderRow(
                accountName = githubStatus?.login.orEmpty(),
                connected = githubStatus?.connected,
                providerHealthy = githubStatus?.providerHealthy == true,
                busy = checking || githubBusy || disconnectingProvider == "github",
                onAdd = { startGitHubAuthorization() },
                onReauthorize = { startGitHubAuthorization() },
                onDisconnect = { pendingDisconnect = "github" },
            )

            if (githubStatusMessage.isNotBlank()) {
                Text(
                    text = githubStatusMessage,
                    color = when {
                        githubStatus?.connected == true && githubStatus?.providerHealthy == true -> ConnectedGreen
                        githubStatus?.connected == true -> Color(0xFFB45309)
                        else -> Muted
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            if (checking) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AppPurple,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Checking connections…",
                        modifier = Modifier.padding(start = 8.dp),
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    color = if (statusMessage.endsWith("disconnected.")) ConnectedGreen else Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            Spacer(modifier = Modifier.size(4.dp))
        }
    }

    if (pendingDisconnect != null) {
        val disconnectTitle = when (pendingDisconnect) {
            "gmail" -> "Disconnect Gmail?"
            "zoho-mail" -> "Disconnect Zoho Mail?"
            else -> "Disconnect GitHub?"
        }
        AlertDialog(
            onDismissRequest = {
                if (disconnectingProvider == null) pendingDisconnect = null
            },
            title = { Text(disconnectTitle, color = Ink, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Sankalp will no longer be able to use this account until you connect it again.",
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = disconnectingProvider == null,
                    onClick = { disconnect(pendingDisconnect.orEmpty()) },
                ) {
                    Text(
                        if (disconnectingProvider != null) "Disconnecting…" else "Disconnect",
                        color = AppPurple,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = disconnectingProvider == null,
                    onClick = { pendingDisconnect = null },
                ) {
                    Text("Cancel", color = Muted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(22.dp),
        )
    }
}

@Composable
private fun MailProviderRow(
    title: String,
    accountAddress: String,
    connected: Boolean?,
    logoRes: Int,
    busy: Boolean,
    onAdd: () -> Unit,
    onReauthorize: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var menuExpanded by remember(title) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFECE7F8)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(15.dp),
                color = PalePurple,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(logoRes),
                        contentDescription = "$title logo",
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                when (connected) {
                    true -> {
                        Text(
                            text = accountAddress.ifBlank { "$title account" },
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        Text(
                            text = "● Connected",
                            color = ConnectedGreen,
                            fontSize = 11.sp,
                        )
                    }
                    false -> Text(
                        text = "Connect your $title account",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    null -> Text(
                        text = "Connection status unavailable",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
            }

            if (connected == true) {
                androidx.compose.foundation.layout.Box {
                    IconButton(
                        enabled = !busy,
                        onClick = { menuExpanded = true },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More options for $title",
                            tint = AppPurple,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = Color.White,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reauthorize", color = Ink) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = AppPurple)
                            },
                            onClick = {
                                menuExpanded = false
                                onReauthorize()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect", color = Ink) },
                            leadingIcon = {
                                Icon(Icons.Outlined.LinkOff, contentDescription = null, tint = AppPurple)
                            },
                            onClick = {
                                menuExpanded = false
                                onDisconnect()
                            },
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = if (connected != true && !busy) AppPurple else Color(0xFFEAE5F7),
                    onClick = { if (connected != true && !busy) onAdd() },
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                color = AppPurple,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Connect $title",
                                tint = Color.White,
                                modifier = Modifier.size(25.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubProviderRow(
    accountName: String,
    connected: Boolean?,
    providerHealthy: Boolean,
    busy: Boolean,
    onAdd: () -> Unit,
    onReauthorize: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFECE7F8)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(15.dp),
                color = PalePurple,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.github_logo),
                        contentDescription = "GitHub logo",
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("GitHub", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                when (connected) {
                    true -> {
                        Text(
                            text = accountName.ifBlank { "GitHub account" },
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        Text(
                            text = if (providerHealthy) "● Connected" else "● Reauthorization needed",
                            color = if (providerHealthy) ConnectedGreen else Color(0xFFB45309),
                            fontSize = 11.sp,
                        )
                    }
                    false -> Text("Connect your GitHub account", color = Muted, fontSize = 12.sp)
                    null -> Text("Connection status unavailable", color = Muted, fontSize = 11.sp)
                }
            }

            if (connected == true) {
                androidx.compose.foundation.layout.Box {
                    IconButton(enabled = !busy, onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More options for GitHub",
                            tint = AppPurple,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = Color.White,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reauthorize", color = Ink) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null, tint = AppPurple) },
                            onClick = {
                                menuExpanded = false
                                onReauthorize()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect", color = Ink) },
                            leadingIcon = { Icon(Icons.Outlined.LinkOff, contentDescription = null, tint = AppPurple) },
                            onClick = {
                                menuExpanded = false
                                onDisconnect()
                            },
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = if (!busy) AppPurple else Color(0xFFEAE5F7),
                    onClick = { if (!busy) onAdd() },
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                color = AppPurple,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Connect GitHub",
                                tint = Color.White,
                                modifier = Modifier.size(25.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
