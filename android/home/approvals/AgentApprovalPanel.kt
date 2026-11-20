package com.indoone.home.approvals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.indoone.home.message.AgentApproval
import com.indoone.home.message.ChatApi
import kotlinx.coroutines.launch

/**
 * Review already-paused official LangGraph /api/agent actions.
 *
 * Normal /api/chat Confirm/Cancel chips are NOT LangGraph approval tokens.
 * Only server-recorded pending actions appear here, and every review fetches
 * exact arguments again before a person can explicitly approve or deny.
 * A submitted decision is NEVER retried, including on network timeout.
 */
@Composable
fun AgentApprovalPanel(modifier: Modifier = Modifier, refreshKey: Int = 0) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var pending by remember(userId) { mutableStateOf(emptyList<AgentApproval>()) }
    var selected by remember(userId) { mutableStateOf<AgentApproval?>(null) }
    var busy by remember(userId) { mutableStateOf(false) }
    var status by remember(userId) { mutableStateOf("") }
    var attempted by remember(userId) { mutableStateOf(emptySet<String>()) }

    fun refresh() {
        if (busy || userId.isBlank()) return
        scope.launch {
            val result = runCatching { ChatApi.listAgentApprovals() }
            if (FirebaseAuth.getInstance().currentUser?.uid != userId) {
                pending = emptyList()
                selected = null
                return@launch
            }
            pending = result.getOrNull().orEmpty().filter { it.runId !in attempted }
            // Do not show stale review controls on a read failure.
            if (result.isFailure) pending = emptyList()
        }
    }

    LaunchedEffect(userId, refreshKey) {
        if (userId.isNotBlank()) refresh()
    }
    DisposableEffect(lifecycle, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    fun review(item: AgentApproval) {
        if (busy || item.runId in attempted || userId.isBlank()) return
        busy = true
        scope.launch {
            try {
                val latest = ChatApi.getAgentApproval(item.runId)
                if (FirebaseAuth.getInstance().currentUser?.uid != userId) return@launch
                if (
                    latest.status != "pending" ||
                    latest.runId != item.runId ||
                    latest.tool != item.tool ||
                    latest.payload != item.payload ||
                    latest.expiresAt != item.expiresAt
                ) {
                    status = "This action changed or expired. Review a new request."
                } else {
                    selected = latest
                    status = ""
                }
            } catch (_: Exception) {
                status = "Could not verify this approval. Nothing was sent."
            } finally {
                busy = false
            }
        }
    }

    fun decide(item: AgentApproval, decision: String) {
        if (busy || item.runId in attempted || userId.isBlank()) return
        busy = true
        attempted = attempted + item.runId
        selected = null
        scope.launch {
            try {
                val result = ChatApi.decideAgentApproval(item.runId, decision)
                if (FirebaseAuth.getInstance().currentUser?.uid != userId) return@launch
                status = when (result.status) {
                    "completed" -> "Action completed and verified."
                    "denied" -> "Action denied. No action was sent."
                    else -> "Outcome needs review. Do not submit this action again."
                }
            } catch (_: Exception) {
                status = "Decision outcome uncertain. Do not retry; check the provider."
            } finally {
                busy = false
                refresh()
            }
        }
    }

    if (userId.isNotBlank() && (pending.isNotEmpty() || status.isNotBlank())) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF8F5FF),
            border = BorderStroke(1.dp, Color(0xFFDACEF2)),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pending agent approvals (" + pending.size + ")", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    TextButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
                }
                pending.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            item.tool,
                            modifier = Modifier.weight(1f).padding(top = 9.dp),
                            fontSize = 12.sp,
                            color = Color(0xFF432C63),
                        )
                        TextButton(onClick = { review(item) }, enabled = !busy) { Text("Review") }
                    }
                }
                if (status.isNotBlank()) {
                    Text(status, fontSize = 12.sp, color = Color(0xFF5E347B))
                }
            }
        }
    }

    selected?.let { action ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) selected = null
            },
            title = { Text("Approve this action?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Review the exact tool and data before deciding. This may change your connected account.")
                    Text("Tool", fontWeight = FontWeight.Bold)
                    Text(action.tool)
                    Text("Exact action payload", fontWeight = FontWeight.Bold)
                    Text(action.payload, fontSize = 12.sp, color = Color(0xFF483B5B))
                    Text("Expires: " + action.expiresAt, fontSize = 11.sp, color = Color(0xFF756D7D))
                }
            },
            confirmButton = {
                TextButton(onClick = { decide(action, "approve") }, enabled = !busy) {
                    Text("Approve once")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { selected = null }, enabled = !busy) {
                        Text("Close")
                    }
                    TextButton(onClick = { decide(action, "deny") }, enabled = !busy) {
                        Text("Deny")
                    }
                }
            },
        )
    }
}
