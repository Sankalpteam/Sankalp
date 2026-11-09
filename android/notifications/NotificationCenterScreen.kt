package com.indoone.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationCenterScreen(
    repository: NotificationRepository,
    onBack: () -> Unit,
) {
    val notifications by repository.observeAll().collectAsState(initial = emptyList())
    val unreadCount by repository.observeUnreadCount().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color(0xFF2C2733))
                }
                Text(
                    "Notifications",
                    Modifier.weight(1f),
                    color = Color(0xFF1F1B24),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (unreadCount > 0) {
                    TextButton(
                        onClick = { scope.launch { repository.markAllRead() } },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text("Mark all as read", color = Color(0xFF5E2DD2), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF0EEF5))

            if (notifications.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(horizontal = 30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No notifications yet", color = Color(0xFF2C2733), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("New Sankalp alerts will appear here.", Modifier.padding(top = 5.dp), color = Color(0xFF8A8392), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        Surface(
                            Modifier.fillMaxWidth(),
                            color = if (notification.isRead) Color.White else Color(0xFFF8F4FF),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    if (!notification.isRead) scope.launch { repository.markRead(notification.id) }
                                }.padding(horizontal = 18.dp, vertical = 14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        notification.title,
                                        Modifier.weight(1f),
                                        color = Color(0xFF292331),
                                        fontSize = 14.sp,
                                        fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold,
                                    )
                                    Text(formatNotificationTime(notification.receivedAt), color = Color(0xFF958D9E), fontSize = 10.sp)
                                }
                                Text(
                                    notification.body,
                                    Modifier.padding(top = 5.dp),
                                    color = Color(0xFF6F6878),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Medium,
                                )
                                Text(
                                    notification.category.replaceFirstChar {
                                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                    },
                                    Modifier.padding(top = 7.dp),
                                    color = Color(0xFF8A8392),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (notification != notifications.last()) HorizontalDivider(color = Color(0xFFF0EEF5))
                    }
                }
            }
        }
    }
}

private fun formatNotificationTime(timestamp: Long): String {
    val delta = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    return when {
        delta < 60_000L -> "Just now"
        delta < 3_600_000L -> "${delta / 60_000L} min ago"
        delta < 86_400_000L -> "${delta / 3_600_000L} hr ago"
        delta < 7L * 86_400_000L -> "${delta / 86_400_000L} d ago"
        else -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
