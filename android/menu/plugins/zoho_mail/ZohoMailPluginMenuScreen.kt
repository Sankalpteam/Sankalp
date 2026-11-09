package com.indoone.menu.plugins.zoho_mail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.R
import kotlinx.coroutines.launch

private val ZohoAppPurple = Color(0xFF5E2DD2)
private val ZohoInk = Color(0xFF272238)
private val ZohoMuted = Color(0xFF777286)
private val ZohoGreen = Color(0xFF218653)
private val ZohoWarning = Color(0xFFB45309)

@Composable
fun ZohoMailPluginMenuScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    var connectionHealthy by remember { mutableStateOf(false) }
    var permissionsComplete by remember { mutableStateOf(false) }
    var missingScopes by remember { mutableStateOf(emptyList<String>()) }
    var reauthorizationRequired by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var connecting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Checking Zoho Mail connection…") }
    var accountAddress by remember { mutableStateOf("") }

    fun refreshConnection() {
        if (loading || connecting) return
        scope.launch {
            loading = true
            connectionHealthy = false
            try {
                val result = ZohoMailPluginApi.getConnectionStatus()
                connected = result.connected
                permissionsComplete = result.permissionsComplete
                missingScopes = result.missingScopes
                reauthorizationRequired = result.reauthorizationRequired
                accountAddress = if (
                    result.connected && "ZohoMail.accounts.READ" !in result.missingScopes
                ) {
                    val accounts = ZohoMailPluginApi.listAccounts()
                    connectionHealthy = accounts.isNotEmpty()
                    accounts.firstOrNull()?.emailAddress.orEmpty()
                } else {
                    connectionHealthy = false
                    ""
                }
                statusMessage = when {
                    !connected -> "Zoho Mail is not connected yet."
                    result.reauthorizationRequired ->
                        "Zoho Mail is connected, but some permissions are missing. Reauthorize to enable every mail action."
                    !result.permissionsComplete ->
                        "Zoho Mail permission status could not be fully verified. Reauthorize before using mail actions."
                    !connectionHealthy ->
                        "Zoho authorization is saved, but mailbox access could not be verified. Refresh or reauthorize."
                    else -> "Connected to Zoho Mail with all required permissions."
                }
            } catch (error: Exception) {
                connectionHealthy = false
                statusMessage = error.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "Could not check Zoho Mail connection."
            } finally {
                loading = false
            }
        }
    }

    fun startAuthorization() {
        if (loading || connecting) return
        connecting = true
        statusMessage = if (connected) {
            "Preparing to reauthorize Zoho Mail…"
        } else {
            "Preparing secure Zoho Mail authorization…"
        }
        scope.launch {
            try {
                val authorizationUrl = ZohoMailPluginApi.createAuthorizationUrl()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)))
                statusMessage = "Finish authorization in your browser, then return here and refresh the connection."
            } catch (error: Exception) {
                statusMessage = error.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "Could not start Zoho Mail authorization."
            } finally {
                connecting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loading = false
        refreshConnection()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = ZohoAppPurple,
                    )
                }
                Text(
                    text = "Zoho Mail",
                    color = ZohoInk,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF7F4FF),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = Color.White,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.zoho_mail_logo),
                                contentDescription = "Zoho Mail logo",
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Connect Zoho Mail",
                            color = ZohoInk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = "Authorize once. Sankalp can then work with permitted email tasks in Chat.",
                            color = ZohoMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }

            if (loading || connecting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ZohoAppPurple,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = if (connecting) "Opening Zoho authorization…" else "Checking connection…",
                        color = ZohoMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 9.dp),
                    )
                }
            }

            Text(
                text = statusMessage,
                color = when {
                    connected && connectionHealthy && permissionsComplete && !reauthorizationRequired -> ZohoGreen
                    connected -> ZohoWarning
                    else -> ZohoMuted
                },
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )

            if (connected && missingScopes.isNotEmpty()) {
                val permissionLabels = missingScopes.mapNotNull { missingScope ->
                    when (missingScope) {
                        "ZohoMail.accounts.READ" -> "mailbox access"
                        "ZohoMail.folders.READ" -> "folder access"
                        "ZohoMail.messages.READ" -> "read emails"
                        "ZohoMail.messages.CREATE" -> "drafts and sending"
                        "ZohoMail.messages.DELETE" -> "delete emails/drafts"
                        else -> null
                    }
                }.distinct()
                Text(
                    text = if (permissionLabels.isNotEmpty()) {
                        "Missing permissions: " + permissionLabels.joinToString(", ")
                    } else {
                        "Some required Zoho Mail permissions are missing."
                    },
                    color = ZohoWarning,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            if (connected && accountAddress.isNotBlank()) {
                Text(
                    text = accountAddress,
                    color = ZohoInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Button(
                onClick = { startAuthorization() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && !connecting,
                colors = ButtonDefaults.buttonColors(containerColor = ZohoAppPurple),
            ) {
                Text(
                    when {
                        connected && reauthorizationRequired -> "REAUTHORIZE FOR FULL ACCESS"
                        connected -> "REAUTHORIZE ZOHO MAIL"
                        else -> "CONNECT ZOHO MAIL"
                    }
                )
            }

            OutlinedButton(
                onClick = { refreshConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && !connecting,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ZohoAppPurple),
            ) {
                Text(if (connected) "REFRESH CONNECTION" else "CHECK CONNECTION")
            }

            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}
