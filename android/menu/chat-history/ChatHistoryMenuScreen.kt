package com.indoone.menu.chathistory

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.indoone.MainActivity
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.home.message.CloudChatConversation
import com.indoone.home.message.CloudChatRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// The brand purple, the same one the drawer, Settings and the welcome use.
// This screen used to be blue (#2168D6), which nothing else in the app is.
private val ChatHistoryPurple = Color(0xFF703BE2)
private val ChatHistoryPurpleSoft = Color(0xFFB78BFF)
private val ChatHistoryInk = Color(0xFF24212A)
private val ChatHistoryMuted = Color(0xFF9A929F)
private val ChatHistoryDanger = Color(0xFFB4232B)

// Today reads as a time, anything older as a date. HomeScreen.kt carries the
// same helper for the welcome's "pick up where you left off" rows.
private fun historyWhenLabel(updatedAtMillis: Long): String {
    if (updatedAtMillis <= 0L) {
        return ""
    }

    val then = Date(updatedAtMillis)
    val now = Calendar.getInstance()
    val other = Calendar.getInstance().apply { time = then }
    val sameDay = now.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

    return SimpleDateFormat(
        if (sameDay) "h:mm a" else "d MMM",
        Locale.getDefault(),
    ).format(then)
}

private fun historyMetaLine(chat: CloudChatConversation): String {
    val parts = mutableListOf<String>()

    if (chat.messageCount > 0) {
        parts += if (chat.messageCount == 1) "1 message" else "${chat.messageCount} messages"
    }

    val whenLabel = historyWhenLabel(chat.updatedAtMillis)

    if (whenLabel.isNotEmpty()) {
        parts += whenLabel
    }

    return parts.joinToString(" · ")
}

@Composable
fun ChatHistoryMenuScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val repository = remember { CloudChatRepository() }
    val context = LocalContext.current
    var conversations by remember { mutableStateOf<List<CloudChatConversation>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            runCatching { repository.loadConversations() }
                .onSuccess {
                    conversations = it
                    status = null
                }
                .onFailure {
                    status = it.message ?: "Chat history could not be loaded right now."
                }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            repository.cleanupExpiredConversations()
            repository.loadConversations()
        }.onSuccess {
            conversations = it
            status = null
        }.onFailure {
            conversations = emptyList()
            status = it.message ?: "Chat history could not be loaded right now."
        }
        loading = false
    }

    DisposableEffect(repository) {
        val listener = repository.addConversationIdListener(
            onChanged = {
                reload()
            },
            onError = { error -> status = error },
        )
        onDispose { repository.removeConversationIdListener(listener) }
    }

    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF5E2DD2),
                    )
                }
                Text(
                    "Chat History",
                    color = Color(0xFF272238),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )

                if (conversations.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.padding(start = 10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFF0E7FF),
                    ) {
                        Text(
                            if (conversations.size == 1) "1 chat" else "${conversations.size} chats",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            color = Color(0xFF5E2DD2),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                "Open a conversation, or remove one.",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                color = ChatHistoryInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )

            when {
                loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = ChatHistoryPurple,
                        )
                        Text(
                            "Loading chats…",
                            modifier = Modifier.padding(top = 10.dp),
                            color = ChatHistoryMuted,
                            fontSize = 12.sp,
                        )
                    }
                }

                status != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = ChatHistoryDanger,
                        )
                        Text(
                            status.orEmpty(),
                            modifier = Modifier.padding(top = 10.dp),
                            color = ChatHistoryDanger,
                            fontSize = 12.sp,
                        )
                    }
                }

                conversations.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = ChatHistoryMuted,
                        )
                        Text(
                            "No saved chats yet.",
                            modifier = Modifier.padding(top = 10.dp),
                            color = Color(0xFF4F4857),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 18.dp),
                    ) {
                        items(conversations, key = { it.id }) { chat ->
                            ChatHistoryRow(
                                chat = chat,
                                onOpen = { onOpenChat(chat.id) },
                                onDelete = {
                                    scope.launch {
                                        runCatching { repository.deleteConversation(chat.id) }
                                            .onSuccess {
                                                conversations = conversations.filterNot { it.id == chat.id }
                                                status = null

                                                // The chat screen may still be holding the conversation
                                                // that was just deleted - its id lives in that screen,
                                                // not here. Sending the next message with that id is
                                                // what made the assistant answer the deleted question.
                                                // Starting a new chat is the same reset the drawer's
                                                // "New Chat" already performs, so it is one behaviour,
                                                // not two.
                                                context.startActivity(
                                                    Intent(context, MainActivity::class.java).apply {
                                                        putExtra("start_new_chat", true)
                                                        addFlags(
                                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                        )
                                                    },
                                                )
                                            }
                                            .onFailure {
                                                status = it.message ?: "Chat could not be deleted."
                                            }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// The row: the 3dp purple edge the welcome's "pick up where you left off" rows
// use, the conversation's own title, how big it is and when it was last
// touched, and the actions behind one button so Delete is no longer a peer of
// the row itself.
@Composable
private fun ChatHistoryRow(
    chat: CloudChatConversation,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFBF9FF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECE4FA)),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.linearGradient(
                            listOf(ChatHistoryPurple, ChatHistoryPurpleSoft),
                        ),
                    ),
            )

            Column(
                modifier = Modifier.weight(1f).padding(start = 13.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    chat.title,
                    color = ChatHistoryInk,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    historyMetaLine(chat),
                    modifier = Modifier.padding(top = 2.dp),
                    color = ChatHistoryMuted,
                    fontSize = 11.sp,
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "Actions",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF8A8492),
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = ChatHistoryDanger) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
