package com.indoone.vibe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.indoone.MainActivity
import com.indoone.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class VibeSessionState(
    val connectionState: String = "Connecting",
    val transcript: String = "Your live conversation will appear here.",
    val isListening: Boolean = false,
    val isSpeakerOn: Boolean = true,
)

/**
 * Owns the Sankalp Vibe conversation.
 *
 * Android only lets a foreground service keep the microphone and keep running
 * once the app leaves the screen, so the socket and the audio engine live here
 * instead of in the composable. This service also posts the ongoing notification
 * whose button ends the session.
 */
class VibeSessionService : Service() {
    companion object {
        const val ACTION_START = "com.indoone.vibe.action.START"
        const val ACTION_STOP = "com.indoone.vibe.action.STOP"
        const val ACTION_TOGGLE_MICROPHONE = "com.indoone.vibe.action.TOGGLE_MICROPHONE"
        const val ACTION_SET_SPEAKER = "com.indoone.vibe.action.SET_SPEAKER"
        const val EXTRA_SPEAKER_ON = "speaker_on"

        private const val CHANNEL_ID = "vibe_session"
        private const val NOTIFICATION_ID = 4201
        private const val WAKE_LOCK_TAG = "Sankalp:VibeSession"

        private val _state = MutableStateFlow(VibeSessionState())
        val state: StateFlow<VibeSessionState> = _state.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, VibeSessionService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VibeSessionService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun toggleMicrophone(context: Context) {
            val intent = Intent(context, VibeSessionService::class.java)
                .setAction(ACTION_TOGGLE_MICROPHONE)
            context.startService(intent)
        }

        fun setSpeaker(context: Context, on: Boolean) {
            val intent = Intent(context, VibeSessionService::class.java)
                .setAction(ACTION_SET_SPEAKER)
                .putExtra(EXTRA_SPEAKER_ON, on)
            context.startService(intent)
        }
    }

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var client: VibeClient
    private lateinit var audioEngine: VibeAudioEngine

    @Volatile
    private var speakerOn = true
    private var wakeLock: PowerManager.WakeLock? = null
    private var clientConnected = false
    private var sessionStopped = false
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        createSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            ACTION_TOGGLE_MICROPHONE -> handleToggleMicrophone()
            ACTION_SET_SPEAKER -> handleSetSpeaker(intent.getBooleanExtra(EXTRA_SPEAKER_ON, true))
        }
        // A live voice conversation cannot be resumed by the system, so never restart it.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopSession()
        super.onDestroy()
    }

    private fun createSession() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        client = VibeClient(scope)
        audioEngine = VibeAudioEngine(
            context = applicationContext,
            onCapturedPcm = { pcm -> client.sendAudio(pcm) },
            onPlaybackEnabled = { speakerOn },
        )
        clientConnected = false
        sessionStopped = false
    }

    private fun handleStart() {
        // Foreground promotion has to happen right away, before any other work.
        startForegroundCompat()
        acquireWakeLock()
        if (sessionStopped) {
            createSession()
        }
        if (clientConnected) {
            return
        }
        _state.value = VibeSessionState()
        clientConnected = true
        client.connect(onEvent = ::handleEvent, onFailure = ::handleFailure)
    }

    private fun handleStop() {
        stopSession()
        stopForegroundCompat()
        stopSelf()
    }

    private fun handleToggleMicrophone() {
        if (audioEngine.isRecording()) {
            audioEngine.stopRecording()
        } else if (_state.value.connectionState == "Connected") {
            audioEngine.startRecording()
        }
        _state.value = _state.value.copy(isListening = audioEngine.isRecording())
        refreshNotification()
    }

    private fun handleSetSpeaker(on: Boolean) {
        speakerOn = on
        if (!on) {
            audioEngine.flushPlayback()
        }
        _state.value = _state.value.copy(isSpeakerOn = on)
    }

    private fun handleEvent(event: JSONObject) {
        when (event.optString("type")) {
            "ready", "connected" -> {
                _state.value = _state.value.copy(connectionState = "Connected")
                if (!audioEngine.isRecording()) {
                    audioEngine.startRecording()
                }
                _state.value = _state.value.copy(isListening = audioEngine.isRecording())
            }
            "status" -> {
                val next = when (event.optString("interaction_status").uppercase()) {
                    "IN_PROGRESS" -> "Thinking…"
                    "IDLE" -> "Connected"
                    else -> _state.value.connectionState
                }
                _state.value = _state.value.copy(connectionState = next)
            }
            "transcript" -> {
                val text = event.optString("text").trim()
                if (text.isNotBlank()) {
                    val line = when (event.optString("role")) {
                        "user" -> "You: $text"
                        "assistant" -> "Sankalp: $text"
                        else -> text
                    }
                    _state.value = _state.value.copy(transcript = line)
                }
            }
            "audio" -> {
                if (speakerOn) {
                    event.optString("audio_base64")
                        .takeIf { it.isNotBlank() }
                        ?.let(audioEngine::playResponse)
                }
            }
            "interrupted" -> audioEngine.flushPlayback()
            "closed" -> {
                _state.value = _state.value.copy(
                    connectionState = "Disconnected",
                    isListening = false,
                )
            }
            "error" -> {
                val detail = event.optString("detail").ifBlank { "Vibe connection failed." }
                _state.value = _state.value.copy(
                    connectionState = "Connection error",
                    isListening = false,
                    transcript = detail,
                )
            }
        }
        refreshNotification()
    }

    private fun handleFailure(message: String) {
        _state.value = _state.value.copy(
            connectionState = "Connection error",
            isListening = false,
            transcript = message,
        )
        refreshNotification()
    }

    private fun stopSession() {
        if (sessionStopped) {
            return
        }
        sessionStopped = true
        audioEngine.stop()
        client.close()
        scope.cancel()
        releaseWakeLock()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sankalp Vibe",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        foregroundStarted = true
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, VibeSessionService::class.java)
            .setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID + 2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val content = RemoteViews(packageName, R.layout.notification_vibe_session)
        content.setImageViewResource(R.id.vibe_notification_icon, R.drawable.ic_indoone_notification)
        content.setImageViewResource(R.id.vibe_notification_close, R.drawable.ic_indoone_close)
        content.setTextViewText(R.id.vibe_notification_title, "Sankalp")
        content.setTextViewText(R.id.vibe_notification_status, statusText())
        content.setOnClickPendingIntent(R.id.vibe_notification_icon, openPendingIntent)
        content.setOnClickPendingIntent(R.id.vibe_notification_close, stopPendingIntent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_indoone_notification)
                .setContentTitle("Sankalp")
                .setContentText(statusText())
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(openPendingIntent)
                .setCustomContentView(content)
                .setCustomBigContentView(content)
                .addAction(R.drawable.ic_indoone_close, "", stopPendingIntent)
                .build()
        } else {
            Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_indoone_notification)
                .setContentTitle("Sankalp")
                .setContentText(statusText())
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(openPendingIntent)
                .setCustomContentView(content)
                .setCustomBigContentView(content)
                .addAction(R.drawable.ic_indoone_close, "", stopPendingIntent)
                .build()
        }
    }

    /**
     * Android only keeps the notification's action in the collapsed row when the
     * notification also has a text line, so the button is visible without tapping
     * the expand arrow.
     */
    private fun statusText(): String {
        val state = _state.value
        if (state.isListening) {
            return "Listening"
        }
        return state.connectionState
    }

    private fun refreshNotification() {
        if (!foregroundStarted) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) {
            return
        }
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }
}
