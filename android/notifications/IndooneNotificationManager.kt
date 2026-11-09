package com.indoone.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.indoone.MainActivity
import java.util.concurrent.atomic.AtomicInteger

object IndooneNotificationManager {
    private val idGenerator = AtomicInteger(10_000)

    fun show(
        context: Context,
        category: NotificationCategory,
        title: String,
        body: String,
        notificationId: Int? = null,
        openRoute: String? = null,
    ): Boolean {
        val preferences = NotificationPreferences(context)
        if (!preferences.isEnabled() || !preferences.isCategoryEnabled(category)) return false

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        IndooneNotificationChannels.create(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            openRoute?.takeIf { it.isNotBlank() }?.let {
                putExtra(EXTRA_NOTIFICATION_ROUTE, it)
            }
        }

        val requestCode = notificationId ?: idGenerator.incrementAndGet()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val priority = if (category == NotificationCategory.SECURITY) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, category.id)
            .setSmallIcon(com.indoone.R.drawable.ic_indoone_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
            true
        }.getOrDefault(false)
    }

    fun showTest(context: Context) {
        show(
            context,
            NotificationCategory.UPDATES,
            "Sankalp Notifications",
            "Notifications are working correctly on this device.",
        )
    }

    const val EXTRA_NOTIFICATION_ROUTE = "notification_route"
}
