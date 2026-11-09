package com.indoone.vibe

import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.indoone.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VibeClient(
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var socket: WebSocket? = null

    fun connect(
        onEvent: (JSONObject) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                    ?: throw IllegalStateException("Please sign in to use Vibe.")

                val token = Tasks.await(user.getIdToken(false)).token
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Could not get the Sankalp authentication token.")

                val request = Request.Builder()
                    .url(buildWebSocketUrl())
                    .header("Authorization", "Bearer $token")
                    .header("X-Indoone-Vibe-Protocol", "indoone.vibe.v1")
                    .build()

                socket?.close(1000, "reconnect")
                socket = client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            scope.launch(Dispatchers.Main) {
                                onEvent(JSONObject().put("type", "transport_open"))
                            }
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val event = runCatching { JSONObject(text) }.getOrElse {
                                JSONObject()
                                    .put("type", "error")
                                    .put("detail", "Vibe backend returned invalid JSON.")
                            }
                            scope.launch(Dispatchers.Main) {
                                onEvent(event)
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            scope.launch(Dispatchers.Main) {
                                onEvent(
                                    JSONObject()
                                        .put("type", "closed")
                                        .put("code", code)
                                        .put("reason", reason),
                                )
                            }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            scope.launch(Dispatchers.Main) {
                                onFailure(
                                    t.message?.takeIf { it.isNotBlank() }
                                        ?: "Vibe backend connection failed.",
                                )
                            }
                        }
                    },
                )
            } catch (error: Exception) {
                scope.launch(Dispatchers.Main) {
                    onFailure(error.message ?: "Vibe connection failed.")
                }
            }
        }
    }

    fun sendAudio(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        socket?.send(
            JSONObject()
                .put("type", "audio")
                .put("audio_base64", Base64.encodeToString(pcm, Base64.NO_WRAP))
                .put("mime_type", "audio/pcm;rate=16000")
                .toString(),
        )
    }

    fun sendAudioStreamEnd() {
        socket?.send(JSONObject().put("type", "audio_stream_end").toString())
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        socket?.send(
            JSONObject()
                .put("type", "text")
                .put("text", text.trim())
                .toString(),
        )
    }

    fun close() {
        socket?.send(JSONObject().put("type", "stop").toString())
        socket?.close(1000, "Vibe ended")
        socket = null
    }

    private fun buildWebSocketUrl(): String {
        val backend = BuildConfig.INDOONE_BACKEND_URL.trimEnd('/')

        if (BuildConfig.INDOONE_CHANNEL == "terminal" &&
            backend != "https://indooneai.duckdns.org" &&
            backend != "https://52.62.244.79" &&
            backend != "http://127.0.0.1:8000"
        ) {
            throw IllegalStateException("Terminal build is configured for an unsupported backend.")
        }

        val websocketBase = when {
            backend.startsWith("https://") -> backend.replaceFirst("https://", "wss://")
            backend.startsWith("http://") -> backend.replaceFirst("http://", "ws://")
            backend.startsWith("wss://") || backend.startsWith("ws://") -> backend
            else -> throw IllegalStateException("Sankalp backend URL must use http:// or https://")
        }

        return "$websocketBase/api/vibe/session"
    }
}
