package com.indoone.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IndooneFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        NotificationPreferences(this).saveFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        val category = data["category"]
            ?.trim()
            ?.lowercase()
            ?.let { raw -> NotificationCategory.entries.firstOrNull { it.id == raw } }
            ?: NotificationCategory.UPDATES

        val title = data["title"]
            ?.takeIf { it.isNotBlank() }
            ?: message.notification?.title
            ?: "Sankalp"

        val body = data["body"]
            ?.takeIf { it.isNotBlank() }
            ?: message.notification?.body
            ?: return

        val route = data["route"]?.takeIf { it.isNotBlank() }
        val showSystemNotification = !IndooneAppState.isForeground
        runCatching {
            runBlocking(Dispatchers.IO) {
                val repository = NotificationRepository.getInstance(applicationContext)
                repository.add(
                    category = category,
                    title = title,
                    body = body,
                    route = route,
                )
            }

            if (showSystemNotification) {
                IndooneNotificationManager.show(
                    context = applicationContext,
                    category = category,
                    title = title,
                    body = body,
                    openRoute = route,
                )
            }
        }
    }
}
