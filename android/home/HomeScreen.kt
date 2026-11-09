package com.indoone.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.content.pm.PackageManager
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.indoone.menu.AppBottomNav
import com.indoone.menu.AppTab
import com.indoone.menu.AppTopBar
import com.indoone.menu.IndooneTopBarLogo
import com.indoone.menu.data.UserDataRepository
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.indoone.home.message.ActionChip
import com.indoone.home.message.parseActionChips
import org.json.JSONArray
import com.indoone.home.message.ChatApi
import com.indoone.home.message.ChatApiException
import com.indoone.home.message.ChatMediaAttachment
import com.indoone.home.message.CloudChatConversation
import com.indoone.home.message.CloudChatRepository
import com.indoone.home.message.MessageFeedbackStore
import com.indoone.home.message.PendingTurnStore
import com.indoone.home.message.SendTurnWorker
import com.indoone.home.plus.PlusAttachmentSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID

private const val MAX_UPLOAD_BYTES = 2_000_000
private const val MAX_CHAT_IMAGE_BYTES = 5_000_000
private const val MAX_CHAT_DOCUMENT_BYTES = 8_000_000
private const val MAX_CHAT_TOTAL_ATTACHMENT_BYTES = 20_000_000
private const val MAX_CHAT_ATTACHMENTS = 10

private fun readLimitedBytes(
    context: Context,
    uri: Uri,
    maxBytes: Int = MAX_UPLOAD_BYTES,
): ByteArray {
    val output = ByteArrayOutputStream()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > maxBytes) {
                throw IllegalArgumentException(
                    "File is too large. Maximum size is ${maxBytes / 1_000_000} MB."
                )
            }
            output.write(buffer, 0, count)
        }
    } ?: throw IllegalArgumentException("Could not open the selected file.")
    return output.toByteArray()
}

private fun supportedAttachmentMimeType(context: Context, uri: Uri, filename: String): String {
    val detected = context.contentResolver.getType(uri)?.trim()?.lowercase()
    val supported = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )
    if (detected != null && detected in supported) return detected

    return when (filename.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> throw IllegalArgumentException(
            "This attachment type is not supported yet. Use JPEG, PNG, WebP, PDF, or DOCX."
        )
    }
}

private fun decodeAttachmentThumbnail(
    context: Context,
    uri: Uri,
    maxDimension: Int = 180,
): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (_: Exception) {
        null
    }
}

private fun isPhotoAttachment(context: Context, attachment: ChatAttachment): Boolean {
    val uri = attachment.uri ?: return false
    val mime = runCatching { context.contentResolver.getType(uri)?.lowercase() }.getOrNull()
    val extension = attachment.displayName.substringAfterLast('.', "").lowercase()
    return mime?.startsWith("image/") == true ||
        extension in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")
}

@Composable
private fun AttachmentImage(
    uri: Uri,
    description: String,
    modifier: Modifier = Modifier,
    thumbnailMaxDimension: Int = 180,
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(
        initialValue = null,
        key1 = uri,
        key2 = thumbnailMaxDimension,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeAttachmentThumbnail(context, uri, thumbnailMaxDimension)
        }
    }
    val bitmap = thumbnail
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(9.dp)),
        )
    } else {
        Box(
            modifier = modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFEDE7F8)),
            contentAlignment = Alignment.Center,
        ) {
            Text("IMG", color = Color(0xFF5E2DD2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PendingAttachmentPreview(
    attachment: ChatAttachment,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val isPhoto = isPhotoAttachment(context, attachment)
    Column(
        modifier = Modifier.width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(64.dp)) {
            if (isPhoto && attachment.uri != null) {
                AttachmentImage(attachment.uri, "Attached photo preview", Modifier.fillMaxSize())
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(9.dp),
                    color = Color(0xFFF0E9FB),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            attachment.displayName.substringAfterLast('.', "").take(4)
                                .uppercase().ifBlank { "FILE" },
                            color = Color(0xFF5E2DD2),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                    .size(22.dp).clip(CircleShape).background(Color(0xFF292431))
                    .clickable(enabled = enabled, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            attachment.displayName,
            color = Color(0xFF5E2DD2),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SentPhotoStack(photos: List<ChatAttachment>) {
    if (photos.isEmpty()) return

    // Keep the sent media compact and right-aligned. The image cards underneath
    // peek out below the front card instead of stretching a purple bubble across chat.
    val cardWidth = 220.dp
    val cardHeight = 156.dp
    // A half-card peek makes the stacked-photo treatment obvious without making
    // long messages huge: render at most three cards; the badge and gallery cover the rest.
    val cardPeek = 72.dp
    val visiblePhotos = photos.take(3)
    val stackHeight = cardHeight + cardPeek * (visiblePhotos.size - 1)

    var showPhotoGallery by remember(photos.map { it.id }) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(stackHeight)
            .clickable { showPhotoGallery = true },
    ) {
        // Draw back cards first so the first selected photo remains in front.
        visiblePhotos.indices.reversed().forEach { index ->
            val photo = visiblePhotos[index]
            val uri = photo.uri ?: return@forEach
            AttachmentImage(
                uri = uri,
                description = if (photos.size == 1) "Sent photo" else "Photo ${index + 1} of ${photos.size}",
                modifier = Modifier
                    .offset(y = cardPeek * index)
                    .width(cardWidth)
                    .height(cardHeight),
            )
        }
        if (photos.size > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xB8000000),
            ) {
                Text(
                    "${photos.size} photos",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (showPhotoGallery) {
        Dialog(
            onDismissRequest = { showPhotoGallery = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF17151D),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${photos.size} photos",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { showPhotoGallery = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("×", color = Color.White, fontSize = 26.sp)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            photo.uri?.let { uri ->
                                AttachmentImage(
                                    uri = uri,
                                    description = "Photo: ${photo.displayName}",
                                    modifier = Modifier.fillMaxWidth().height(280.dp),
                                    thumbnailMaxDimension = 1000,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SentAttachmentContent(attachments: List<ChatAttachment>) {
    val context = LocalContext.current
    val photos = attachments.filter { it.uri != null && isPhotoAttachment(context, it) }
    val otherFiles = attachments.filterNot { it.uri != null && isPhotoAttachment(context, it) }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (photos.isNotEmpty()) {
            SentPhotoStack(photos)
        }
        otherFiles.forEach { attachment ->
            Surface(
                modifier = Modifier.widthIn(max = 220.dp),
                shape = RoundedCornerShape(9.dp),
                color = Color(0xFF8055DC),
                border = BorderStroke(1.dp, Color(0xFFB69AEF)),
            ) {
                Text(
                    attachment.displayName,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun appendVoiceTranscript(existingText: String, spokenText: String): String {
    val base = existingText.trimEnd()
    val spoken = spokenText.trim()
    if (spoken.isEmpty()) return base
    return if (base.isEmpty()) spoken else "$base $spoken"
}

private data class ChatAttachment(
    val displayName: String,
    val uri: Uri? = null,
    val fileId: String? = null,
    val id: String = UUID.randomUUID().toString(),
)

/**
 * Hand the conversation to Android's print framework.
 *
 * A PDF is what the print dialog produces; building one by hand would mean carrying a PDF
 * library for a document the platform already knows how to lay out. The HTML is generated
 * here, and no script runs in it.
 */
private fun exportChatAsPdf(context: Context, messages: List<ChatMessage>) {
    if (messages.isEmpty()) {
        Toast.makeText(context, "Nothing to export yet", Toast.LENGTH_SHORT).show()
        return
    }

    val body = messages.joinToString("") { message ->
        val who = if (message.fromUser) "You" else "Indoone AI"
        val background = if (message.fromUser) "#F4F2FF" else "#FFFFFF"
        val escaped = message.text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")

        "<div class=\"row\" style=\"background:$background\">" +
            "<div class=\"who\">$who</div><div class=\"text\">$escaped</div></div>"
    }

    val html = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"/>
        <style>
          body { font-family: sans-serif; padding: 24px; color: #242129; }
          h1 { font-size: 18px; color: #5E2DD2; }
          .row { border-radius: 10px; padding: 10px 12px; margin-bottom: 8px; border: 1px solid #E5E0ED; }
          .who { font-size: 11px; color: #6B6478; margin-bottom: 4px; }
          .text { font-size: 13px; line-height: 1.45; }
        </style></head><body><h1>Indoone chat</h1>$body</body></html>
    """.trimIndent()

    val webView = WebView(context)
    webView.settings.javaScriptEnabled = false
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = webView.createPrintDocumentAdapter("Indoone chat")
            printManager.print("Indoone chat", adapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}


private data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val attachments: List<ChatAttachment> = emptyList(),
    // Follow-ups the backend offered with this reply: Confirm/Cancel for a held write, or
    // the obvious next step after a list. Empty for most answers.
    val chips: List<ActionChip> = emptyList(),
)

private enum class ChatActivityState {
    IDLE,
    RECEIVED,
    THINKING,
    PREPARING,
}

private fun ChatActivityState.label(): String = when (this) {
    ChatActivityState.IDLE -> ""
    ChatActivityState.RECEIVED -> "Received"
    ChatActivityState.THINKING -> "Thinking…"
    ChatActivityState.PREPARING -> "Preparing your answer…"
}

// The welcome greets by the hour, the way the web's welcome does.
private fun greetingLine(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

// The web shows a time for today's conversation and a date for an older one.
private fun chatWhenLabel(updatedAtMillis: Long): String {
    if (updatedAtMillis <= 0L) {
        return ""
    }

    val then = java.util.Date(updatedAtMillis)
    val now = java.util.Calendar.getInstance()
    val other = java.util.Calendar.getInstance().apply { time = then }
    val sameDay = now.get(java.util.Calendar.YEAR) == other.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == other.get(java.util.Calendar.DAY_OF_YEAR)

    return java.text.SimpleDateFormat(
        if (sameDay) "HH:mm" else "dd MMM",
        java.util.Locale.getDefault(),
    ).format(then)
}

// One "pick up where you left off" row: the 3dp accent, the conversation's own
// title, and when it was last touched.
@Composable
private fun RecentChatRow(
    title: String,
    whenLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFBF9FF),
        border = BorderStroke(1.dp, Color(0xFFECE4FA)),
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
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(
                                Color(0xFF703BE2),
                                Color(0xFFB78BFF),
                            ),
                        ),
                    ),
            )
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp, top = 10.dp, bottom = 10.dp),
                color = Color(0xFF352742),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                whenLabel,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                color = Color(0xFF9A929F),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun RowScope.QuickPromptChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        // Filled, not outlined: an outline reads as a secondary button, and on
        // an empty screen these are the first thing to press.
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0E7FF),
        border = BorderStroke(1.dp, Color(0xFFE0D1FA)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Color(0xFF5E2DD2),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun IndooneHomeLogo(modifier: Modifier = Modifier) {
    // One logo, one definition: this used to be a second copy of the old diamond.
    IndooneTopBarLogo(modifier = modifier)
}

@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onLobbyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    startNewChat: Boolean = false,
    openConversationId: String? = null,
    onVibeClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onNotificationClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val userKey = remember { FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() } ?: "" }
    val cloudChatRepository = remember { CloudChatRepository() }
    val userDataRepository = remember { UserDataRepository() }

    var input by remember { mutableStateOf("") }
    var messages by remember(userKey, startNewChat) { mutableStateOf(emptyList<ChatMessage>()) }
    var isSending by remember { mutableStateOf(false) }
    var chatActivityState by remember { mutableStateOf(ChatActivityState.IDLE) }
    var conversationId by rememberSaveable(userKey, startNewChat) { mutableStateOf<String?>(null) }

    // The thread menu's rename dialog. The title is seeded from what the thread is called
    // now, so the field opens with something to edit rather than an empty box.
    var renameOpen by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf("") }
    // The thread menu belongs to the conversation, not to the app chrome: it sits inside
    // the chat container, under the divider, so it reads as "this chat" and never collides
    // with the notification bell.
    var threadMenuOpen by remember { mutableStateOf(false) }
    var pendingAttachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var isPlusMenuOpen by remember { mutableStateOf(false) }
    var isAddingAttachments by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var voiceInputBase by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var isLoadingHistory by remember { mutableStateOf(!startNewChat) }
    var welcomeMessageIndex by remember { mutableStateOf(0) }
    var welcomeName by remember { mutableStateOf("") }
    var recentChats by remember { mutableStateOf(emptyList<CloudChatConversation>()) }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()

    // One speech engine for the screen, not one per bubble: each engine is a service
    // connection, and a thread of twenty replies would open twenty of them. The
    // listener is what turns the button back to "Listen" when the reading finishes,
    // rather than leaving it saying "Stop" after the audio has gone.
    // The turn the activity indicator belongs to. Without it the indicator is inferred
    // from a delay, and a fast reply makes it flash back to Thinking after the answer.
    var activeTurnId by remember { mutableStateOf<String?>(null) }
    var activeTurnStartedAt by remember { mutableStateOf(0L) }

    var speakingText by remember { mutableStateOf<String?>(null) }
    val speechEngine = remember { arrayOfNulls<TextToSpeech>(1) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            val ready = speechEngine[0]

            if (status == TextToSpeech.SUCCESS && ready != null) {
                // A voice chosen for the assistant rather than the platform default, which
                // on most phones is the same robotic one every app uses. Indian English
                // first, then any English voice the engine has without a network, then
                // whatever it offers. A genuinely branded character voice needs a cloud
                // speech service; this is the best a system engine can do.
                val voices = ready.voices?.toList().orEmpty()
                val english = voices.filter { it.locale?.language == "en" }
                val chosen = english.firstOrNull { it.locale?.country == "IN" }
                    ?: english.firstOrNull { !it.isNetworkConnectionRequired }
                    ?: english.firstOrNull()
                    ?: voices.firstOrNull()

                if (chosen != null) {
                    ready.voice = chosen
                } else {
                    ready.language = Locale.UK
                }

                // A shade slower and slightly lower than the default: the default reads
                // like a screen reader, and a reply read at speed is hard to follow.
                ready.setPitch(0.95f)
                ready.setSpeechRate(0.98f)
            }
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                speakingText = null
            }

            @Deprecated("Kept for the platform's older callback")
            override fun onError(utteranceId: String?) {
                speakingText = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                speakingText = null
            }
        })

        speechEngine[0] = engine

        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    fun speakOrStop(text: String) {
        val engine = speechEngine[0] ?: return

        if (speakingText == text) {
            engine.stop()
            speakingText = null
            return
        }

        engine.stop()
        speakingText = text
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "indoone-reply")
    }

    LaunchedEffect(messages.isEmpty()) {
        if (messages.isEmpty()) {
            welcomeMessageIndex = 0
            while (true) {
                kotlinx.coroutines.delay(2000)
                welcomeMessageIndex = 1 - welcomeMessageIndex
            }
        }
    }

    // A text-only turn is sent by SendTurnWorker, so nothing here waits on the network.
    // This reconciles what the worker settled: a delivered turn becomes the reply bubble,
    // a failed one becomes the failure and puts the text back in the composer.
    //
    // The store hands each settled turn over once and forgets it, so this poll cannot
    // append the same reply twice. The text is never re-sent from here - a failed turn
    // may have been a GitHub write, and repeating it is the user's decision, not a loop's.
    LaunchedEffect(conversationId, userKey) {
        val store = PendingTurnStore.get(context)

        while (true) {
            delay(1000L)

            val current = conversationId ?: continue

            // Received becomes Thinking here, from one owner, and only while the turn is
            // still in flight. This is the fix for the indicator flashing back: it used
            // to be a delayed coroutine that could fire after the answer had landed.
            if (
                activeTurnId != null &&
                chatActivityState == ChatActivityState.RECEIVED &&
                System.currentTimeMillis() - activeTurnStartedAt > 350L
            ) {
                chatActivityState = ChatActivityState.THINKING
            }

            val settled = withContext(Dispatchers.IO) { store.takeSettled(current) }

            if (settled.isEmpty()) {
                continue
            }

            for (turn in settled) {
                val reply = turn.reply

                if (turn.status == PendingTurnStore.STATUS_DELIVERED && !reply.isNullOrBlank()) {
                    // The last stage of the same sequence: Preparing, then the answer and
                    // its actions. Received and Thinking came from the send.
                    chatActivityState = ChatActivityState.PREPARING
                    delay(600L)
                    // The chips came back with the reply; restore them, so a turn that
                    // finished in the background keeps its buttons. A row written before
                    // the column existed has null here, which reads as no chips.
                    val restoredChips = parseActionChips(
                        turn.actionChips?.let { raw ->
                            runCatching { JSONArray(raw) }.getOrNull()
                        }
                    )
                    messages = messages + ChatMessage(reply, false, chips = restoredChips)
                    runCatching {
                        withContext(Dispatchers.IO) {
                            cloudChatRepository.appendMessage(
                                conversationId = turn.conversationId,
                                role = "assistant",
                                content = reply,
                            )
                        }
                    }
                } else {
                    // Back in the box where the user left it, exactly as the web does.
                    if (input.isBlank()) {
                        input = turn.text
                    }
                    messages = messages + ChatMessage(
                        turn.error ?: "Indoone AI could not complete the request.",
                        false,
                    )
                }
            }

            // The turn is settled, so the indicator belongs to nothing any more.
            activeTurnId = null
            chatActivityState = ChatActivityState.IDLE
            isSending = false
        }
    }

    // The greeting's name and the "pick up where you left off" rows. Both come
    // from the account - the profile in users/<uid>/profile, and the same
    // conversations Chat History lists - which is where the web reads them too.
    LaunchedEffect(userKey) {
        if (userKey.isBlank()) {
            welcomeName = ""
            recentChats = emptyList()
            return@LaunchedEffect
        }

        welcomeName = runCatching {
            (userDataRepository.readProfile()["name"] as? String)?.trim().orEmpty()
        }.getOrDefault("")

        recentChats = runCatching {
            cloudChatRepository.loadConversations(limit = 3)
        }.getOrDefault(emptyList<CloudChatConversation>())
            .filter { it.id != conversationId }
            .take(2)
    }

    // Land on the newest message when a conversation opens, and stay there as replies
    // arrive. Three passes over three frames, because the list is measured in stages:
    // the first frame knows the item count, the next ones know the real heights.
    LaunchedEffect(isLoadingHistory, messages.size, chatActivityState, startNewChat, openConversationId) {
        if (!isLoadingHistory && (messages.isNotEmpty() || chatActivityState != ChatActivityState.IDLE)) {
            // A large positive scroll offset on the final item clamps the viewport to the
            // real list bottom, rather than to the top of a long final message.
            repeat(3) {
                withFrameNanos { }
                val lastItemIndex = chatListState.layoutInfo.totalItemsCount - 1
                if (lastItemIndex >= 0) {
                    chatListState.scrollToItem(
                        lastItemIndex,
                        scrollOffset = Int.MAX_VALUE / 4,
                    )
                }
            }
        }
    }

    LaunchedEffect(userKey, startNewChat, openConversationId) {
        if (userKey.isBlank() || startNewChat) {
            conversationId = null
            messages = emptyList()
            isLoadingHistory = false
            return@LaunchedEffect
        }
        isLoadingHistory = true
        runCatching {
            cloudChatRepository.cleanupExpiredConversations()
            if (!openConversationId.isNullOrBlank()) {
                cloudChatRepository.loadConversation(openConversationId)
            } else {
                cloudChatRepository.loadLatestChat()
            }
        }.onSuccess { chat ->
            conversationId = chat?.id
            messages = chat?.messages?.map {
                ChatMessage(it.content, it.role == "user")
            }.orEmpty()
        }.onFailure {
            conversationId = null
            messages = emptyList()
        }
        isLoadingHistory = false
    }

    // The welcome's "pick up where you left off" rows open a conversation the
    // same way Chat History does: read it back and put it in the thread.
    fun openWelcomeConversation(id: String) {
        scope.launch {
            isLoadingHistory = true
            runCatching { cloudChatRepository.loadConversation(id) }
                .onSuccess { chat ->
                    conversationId = chat?.id
                    messages = chat?.messages?.map {
                        ChatMessage(it.content, it.role == "user")
                    }.orEmpty()
                }
                .onFailure {
                    conversationId = null
                    messages = emptyList()
                }
            isLoadingHistory = false
        }
    }

    fun sendMessage() {
        val typed = input.trim()
        val attachmentsToSend = pendingAttachments.toList()
        val hasPendingAttachment = attachmentsToSend.isNotEmpty()
        if (isSending || isAddingAttachments || (typed.isEmpty() && !hasPendingAttachment)) return

        val messageToBackend = typed.ifBlank {
            "Please inspect the attached file(s) and describe their contents or visible details."
        }
        val requestConversationId = conversationId ?: UUID.randomUUID().toString()
        messages = messages + ChatMessage(typed, true, attachmentsToSend)
        input = ""
        conversationId = requestConversationId
        isSending = true
        chatActivityState = ChatActivityState.RECEIVED

        val attachmentSummary = attachmentsToSend
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.displayName }
            ?.let { "[Attachments: $it]" }
        val persistedUserContent = listOf(typed, attachmentSummary.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString("\n")

        // A text-only turn is handed to the application, not to this composition. The
        // send used to run in `scope`, which is cancelled the moment the screen goes
        // away - backgrounding the app dropped the request and the reply with it.
        //
        // The turn is written first, so a process death leaves something to reconcile
        // against instead of a question with no answer.
        if (!hasPendingAttachment) {
            // Foreground stream first: the words appear as they are produced, and the
            // activity indicator goes on the first one instead of after the whole answer.
            //
            // The durable worker below is still the fallback. If the stream cannot start,
            // or dies before a single token, the turn is handed to it exactly as before -
            // so a turn that may be a GitHub write still survives a process death.
            scope.launch {
                var streamedIndex = -1

                val streamed = runCatching {
                    ChatApi.streamMessage(
                        message = messageToBackend,
                        conversationId = requestConversationId,
                        onMeta = { id -> conversationId = id },
                        onDelta = { full ->
                            // First token: the wait is over.
                            chatActivityState = ChatActivityState.IDLE

                            if (streamedIndex < 0) {
                                messages = messages + ChatMessage(full, false)
                                streamedIndex = messages.lastIndex
                            } else {
                                val updated = messages.toMutableList()
                                updated[streamedIndex] = updated[streamedIndex].copy(text = full)
                                messages = updated
                            }
                        },
                    )
                }

                streamed.getOrNull()?.let { response ->
                    conversationId = response.conversationId

                    if (streamedIndex < 0) {
                        messages = messages + ChatMessage(
                            response.reply,
                            false,
                            chips = response.actionChips,
                        )
                    } else {
                        val updated = messages.toMutableList()
                        updated[streamedIndex] = updated[streamedIndex].copy(
                            text = response.reply,
                            chips = response.actionChips,
                        )
                        messages = updated
                    }

                    chatActivityState = ChatActivityState.IDLE
                    isSending = false

                    runCatching {
                        withContext(Dispatchers.IO) {
                            cloudChatRepository.appendMessage(
                                conversationId = response.conversationId,
                                role = "assistant",
                                content = response.reply,
                            )
                        }
                    }

                    return@launch
                }

                // A partial bubble is removed before the worker takes over, so the
                // fallback cannot leave half an answer above the real one.
                if (streamedIndex >= 0 && streamedIndex < messages.size) {
                    val updated = messages.toMutableList()
                    updated.removeAt(streamedIndex)
                    messages = updated
                }

                val store = PendingTurnStore.get(context)
                val turn = store.begin(requestConversationId, messageToBackend)
                val request = OneTimeWorkRequestBuilder<SendTurnWorker>()
                .setInputData(workDataOf(SendTurnWorker.KEY_TURN_ID to turn.id))
                .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                SendTurnWorker.workName(turn.id),
                // One work item per turn: a double tap cannot send the same text twice.
                ExistingWorkPolicy.KEEP,
                request,
                )

                // The live activity stays, and the reconciliation effect below owns every
                // stage of it. A coroutine here that flipped to Thinking after a delay used
                // to race that effect: on a fast reply it fired after the answer had landed
                // and the indicator reappeared. One owner, one order: Received, Thinking,
                // Preparing, done.
                isSending = true
                activeTurnId = turn.id
                activeTurnStartedAt = System.currentTimeMillis()
                chatActivityState = ChatActivityState.RECEIVED

                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            cloudChatRepository.appendMessage(
                                conversationId = requestConversationId,
                                role = "user",
                                content = persistedUserContent,
                            )
                        }
                    }
                }
            }
            return@sendMessage
        }

        scope.launch {
            val receivedStage = async { delay(350L) }
            val userSaveDeferred = async(Dispatchers.IO) {
                runCatching {
                    cloudChatRepository.appendMessage(
                        conversationId = requestConversationId,
                        role = "user",
                        content = persistedUserContent,
                    )
                }
            }
            val backendDeferred = async(Dispatchers.IO) {
                runCatching {
                    val fileIds = attachmentsToSend.mapNotNull { it.fileId }
                    val mediaPayloads = mutableListOf<ChatMediaAttachment>()
                    var totalBytes = 0
                    for (attachment in attachmentsToSend) {
                        val uri = attachment.uri ?: continue
                        val mimeType = supportedAttachmentMimeType(context, uri, attachment.displayName)
                        val maxAttachmentBytes = if (mimeType.startsWith("image/")) {
                            MAX_CHAT_IMAGE_BYTES
                        } else {
                            MAX_CHAT_DOCUMENT_BYTES
                        }
                        val bytes = readLimitedBytes(context, uri, maxAttachmentBytes)
                        totalBytes += bytes.size
                        if (totalBytes > MAX_CHAT_TOTAL_ATTACHMENT_BYTES) {
                            throw IllegalArgumentException(
                                "Attachments together exceed the 20 MB upload limit. Remove or reduce some files and try again."
                            )
                        }
                        mediaPayloads += ChatMediaAttachment(
                            filename = attachment.displayName,
                            mimeType = mimeType,
                            bytes = bytes,
                        )
                    }
                    ChatApi.sendMessage(
                        message = messageToBackend,
                        conversationId = requestConversationId,
                        fileIds = fileIds,
                        mediaAttachments = mediaPayloads,
                    )
                }
            }

            receivedStage.await()
            chatActivityState = ChatActivityState.THINKING
            val backendResult = backendDeferred.await()

            backendResult.onSuccess { response ->
                conversationId = response.conversationId
                if (pendingAttachments.map { it.id } == attachmentsToSend.map { it.id }) {
                    pendingAttachments = emptyList()
                }
                chatActivityState = ChatActivityState.PREPARING
                delay(600L)
                messages = messages + ChatMessage(
                    response.reply,
                    false,
                    chips = response.actionChips,
                )
                chatActivityState = ChatActivityState.IDLE

                var userSave = userSaveDeferred.await()
                if (userSave.isFailure) {
                    userSave = runCatching {
                        withContext(Dispatchers.IO) {
                            cloudChatRepository.appendMessage(
                                conversationId = requestConversationId,
                                role = "user",
                                content = persistedUserContent,
                            )
                        }
                    }
                }
                if (userSave.isFailure) {
                    val reason = userSave.exceptionOrNull()?.message
                        ?.takeIf { it.isNotBlank() }
                        ?: "Unknown cloud error."
                    messages = messages + ChatMessage(
                        "Chat reply received, but this message could not be saved to the cloud: $reason",
                        false,
                    )
                } else {
                    val assistantSave = runCatching {
                        withContext(Dispatchers.IO) {
                            cloudChatRepository.appendMessage(
                                conversationId = response.conversationId,
                                role = "assistant",
                                content = response.reply,
                            )
                        }
                    }
                    if (assistantSave.isFailure) {
                        val reason = assistantSave.exceptionOrNull()?.message
                            ?.takeIf { it.isNotBlank() }
                            ?: "Unknown cloud error."
                        messages = messages + ChatMessage(
                            "Chat reply received, but the reply could not be saved to the cloud: $reason",
                            false,
                        )
                    }
                }
            }.onFailure { error ->
                receivedStage.await()
                chatActivityState = ChatActivityState.IDLE
                val userSave = userSaveDeferred.await()
                if (userSave.isFailure) {
                    val reason = userSave.exceptionOrNull()?.message
                        ?.takeIf { it.isNotBlank() }
                        ?: "Unknown cloud error."
                    messages = messages + ChatMessage(
                        "Message could not be saved to the cloud: $reason",
                        false,
                    )
                }
                if (input.isBlank()) input = typed
                val failureReply = error.message ?: "Indoone AI could not complete the request."
                messages = messages + ChatMessage(failureReply, false)
                // A reply the backend actually sent is part of the conversation, so
                // save it: refreshing the chat then shows what was said at the time.
                if ((error as? ChatApiException)?.statusCode != null) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            cloudChatRepository.appendMessage(
                                conversationId = requestConversationId,
                                role = "assistant",
                                content = failureReply,
                            )
                        }
                    }
                }
            }
            isSending = false
            chatActivityState = ChatActivityState.IDLE
        }
    }

    fun displayNameForUri(uri: Uri, fallback: String): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }?.takeIf { it.isNotBlank() } ?: fallback

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capturedUri = cameraCaptureUri
        cameraCaptureUri = null
        if (success && capturedUri != null) {
            if (pendingAttachments.size >= MAX_CHAT_ATTACHMENTS) {
                val fileName = capturedUri.lastPathSegment
                    ?.takeIf { capturedUri.authority == "${context.packageName}.fileprovider" }
                fileName?.let { File(File(context.cacheDir, "camera"), it).delete() }
                messages = messages + ChatMessage("You can attach up to 10 files per message.", false)
            } else {
                pendingAttachments = pendingAttachments + ChatAttachment(
                    displayName = "Camera photo",
                    uri = capturedUri,
                )
            }
        } else {
            val fileName = capturedUri?.lastPathSegment
                ?.takeIf { capturedUri.authority == "${context.packageName}.fileprovider" }
            fileName?.let { File(File(context.cacheDir, "camera"), it).delete() }
        }
    }

    val photosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_CHAT_ATTACHMENTS),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val remaining = MAX_CHAT_ATTACHMENTS - pendingAttachments.size
        if (remaining <= 0) {
            messages = messages + ChatMessage("You can attach up to 10 files per message.", false)
            return@rememberLauncherForActivityResult
        }
        val selected = uris.take(remaining)
        if (selected.size < uris.size) {
            messages = messages + ChatMessage(
                "You can attach up to 10 files per message. Only the remaining $remaining item(s) were added.",
                false,
            )
        }
        pendingAttachments = pendingAttachments + selected.map { uri ->
            ChatAttachment(displayName = displayNameForUri(uri, "Photo"), uri = uri)
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val remaining = MAX_CHAT_ATTACHMENTS - pendingAttachments.size
        if (remaining <= 0) {
            messages = messages + ChatMessage("You can attach up to 10 files per message.", false)
            return@rememberLauncherForActivityResult
        }
        val selected = uris.take(remaining)
        if (selected.size < uris.size) {
            messages = messages + ChatMessage(
                "You can attach up to 10 files per message. Only the remaining $remaining item(s) were added.",
                false,
            )
        }
        scope.launch {
            isAddingAttachments = true
            try {
                for (uri in selected) {
                    val name = displayNameForUri(uri, "file")
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension in setOf("txt", "md", "json", "csv", "log")) {
                        runCatching {
                            val bytes = withContext(Dispatchers.IO) { readLimitedBytes(context, uri) }
                            val upload = withContext(Dispatchers.IO) { ChatApi.uploadTextFile(name, bytes) }
                            ChatAttachment(
                                displayName = upload.filename.ifBlank { name },
                                fileId = upload.fileId,
                            )
                        }.onSuccess { attachment ->
                            pendingAttachments = (pendingAttachments + attachment).take(MAX_CHAT_ATTACHMENTS)
                        }.onFailure { error ->
                            messages = messages + ChatMessage(
                                error.message ?: "Could not attach $name.",
                                false,
                            )
                        }
                    } else {
                        val supported = runCatching { supportedAttachmentMimeType(context, uri, name) }
                        if (supported.isSuccess) {
                            pendingAttachments = (
                                pendingAttachments + ChatAttachment(displayName = name, uri = uri)
                            ).take(MAX_CHAT_ATTACHMENTS)
                        } else {
                            messages = messages + ChatMessage(
                                supported.exceptionOrNull()?.message
                                    ?: "This attachment type is not supported: $name",
                                false,
                            )
                        }
                    }
                }
            } finally {
                isAddingAttachments = false
            }
        }
    }

    fun launchCameraCapture() {
        if (pendingAttachments.size >= MAX_CHAT_ATTACHMENTS) {
            messages = messages + ChatMessage("You can attach up to 10 files per message.", false)
            return
        }
        runCatching {
            val cameraDirectory = File(context.cacheDir, "camera").apply { mkdirs() }
            val captureFile = File(cameraDirectory, "indoone-camera-${UUID.randomUUID()}.jpg")
            val captureUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                captureFile,
            )
            cameraCaptureUri = captureUri
            cameraLauncher.launch(captureUri)
        }.onFailure { error ->
            messages = messages + ChatMessage(error.message ?: "Could not open the camera.", false)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            messages = messages + ChatMessage(
                "Camera permission is required. Allow Camera in Android Settings > Apps > Indoone > Permissions.",
                false,
            )
        }
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    fun stopVoiceInput() {
        speechRecognizer?.stopListening()
    }

    fun startVoiceInput() {
        if (isSending) return
        if (speechRecognizer == null) {
            voiceError = "Speech recognition is not available on this device."
            return
        }

        voiceError = null
        voiceInputBase = input.trimEnd()
        isListening = true
        speechRecognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

            // Prefer well-formatted final text without forcing English as the language.
            if (Build.VERSION.SDK_INT >= 33) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY,
                )
            }

            // Android 14+ supports automatic spoken-language detection and switching.
            // Keep every language Indoone supports available as a recognition choice.
            // This list is what the recogniser may detect and switch between, so a
            // missing entry means that language cannot be spoken to the app at all.
            if (Build.VERSION.SDK_INT >= 34) {
                // Every language India recognises, plus English. The Vibe model is a
                // Gemini 3.8 Live model, which supports well over seventy languages, so
                // the limit here is the list, not the model. A language missing from it
                // cannot be detected or switched to, which is how this started with four.
                val supportedVoiceLanguages = arrayListOf(
                    "en-IN",
                    "en-US",
                    "as-IN",
                    "bn-IN",
                    "brx-IN",
                    "doi-IN",
                    "gu-IN",
                    "hi-IN",
                    "kn-IN",
                    "kok-IN",
                    "ks-IN",
                    "mai-IN",
                    "ml-IN",
                    "mni-IN",
                    "mr-IN",
                    "ne-IN",
                    "or-IN",
                    "pa-IN",
                    "sa-IN",
                    "sat-IN",
                    "sd-IN",
                    "ta-IN",
                    "te-IN",
                    "ur-IN",
                )
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    ArrayList(supportedVoiceLanguages),
                )
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                    RecognizerIntent.LANGUAGE_SWITCH_BALANCED,
                )
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    ArrayList(supportedVoiceLanguages),
                )
            }
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceInput() else voiceError = "Microphone permission is required."
    }

    DisposableEffect(speechRecognizer) {
        if (speechRecognizer == null) {
            onDispose { }
        } else {
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }

                override fun onBeginningOfSpeech() {
                    isListening = true
                }

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    // Keep the Voice button visible until final recognition results
                    // arrive. This prevents Send from appearing over partial speech.
                }

                override fun onError(error: Int) {
                    isListening = false
                    voiceError = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "I could not hear that clearly. Try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        else -> "Voice input failed. Please try again."
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) input = appendVoiceTranscript(voiceInputBase, spoken)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val spoken = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) input = appendVoiceTranscript(voiceInputBase, spoken)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            onDispose {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
            }
        }
    }

    fun onVoiceButtonClick() {
        val activity = context as? Activity ?: return
        if (isListening) {
            stopVoiceInput()
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceInput()
        } else if (!activity.isFinishing) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val shareCurrentChat: () -> Unit = {
        val thread = conversationId

        if (thread.isNullOrBlank()) {
            Toast.makeText(context, "Send a message first", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                runCatching { ChatApi.shareThread(thread) }
                    .onSuccess { url ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Indoone chat", url))
                        Toast.makeText(
                            context,
                            "Link copied to clipboard! Anyone with this link can view this chat",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    .onFailure {
                        Toast.makeText(context, "Could not create a share link", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    val renameCurrentChat: () -> Unit = {
        if (!conversationId.isNullOrBlank()) {
            renameTitle = ""
            renameOpen = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                onMenuClick = onMenuClick,
                hasUnreadNotifications = hasUnreadNotifications,
                onNotificationsClick = onNotificationClick,
            )

            if (renameOpen) {
                AlertDialog(
                    onDismissRequest = { renameOpen = false },
                    title = { Text("Rename chat") },
                    text = {
                        OutlinedTextField(
                            value = renameTitle,
                            onValueChange = { renameTitle = it },
                            singleLine = true,
                            placeholder = { Text("A name for this chat") },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val thread = conversationId
                                val title = renameTitle.trim()
                                renameOpen = false

                                if (thread.isNullOrBlank() || title.isBlank()) return@TextButton

                                scope.launch {
                                    runCatching { ChatApi.renameThread(thread, title) }
                                        .onFailure {
                                            Toast.makeText(context, "Could not rename this chat", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            },
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
                    },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            if (!isLoadingHistory && messages.isNotEmpty()) {
                // zIndex is the fix, not the styling: this Box is declared BEFORE the
                // LazyColumn in the same parent, so the list was drawn over the button and
                // took the taps. A bare glyph also read as broken rather than as a control,
                // hence the circular surface and the 40dp touch target.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 12.dp)
                        .zIndex(10f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, Color(0xFFE5E0ED)),
                        shadowElevation = 2.dp,
                    ) {
                        IconButton(
                            onClick = { threadMenuOpen = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Text("⋮", color = Color(0xFF352742), fontSize = 20.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = threadMenuOpen,
                        onDismissRequest = { threadMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share Chat Link") },
                            onClick = { threadMenuOpen = false; shareCurrentChat() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export as PDF") },
                            onClick = { threadMenuOpen = false; exportChatAsPdf(context, messages) },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename Chat") },
                            onClick = { threadMenuOpen = false; renameCurrentChat() },
                        )
                    }
                }
            }

            if (isLoadingHistory) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF703BE2),
                        strokeWidth = 2.2.dp,
                    )
                }
            } else LazyColumn(
                // The scroll effect above drives this state. Without it the list ran on its
                // own internal state, so every conversation opened at the first message
                // and the scroll to the bottom went nowhere.
                state = chatListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = if (messages.isEmpty()) Arrangement.Center else Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 26.dp, bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // The greeting. It used to say a flat "Indoone AI";
                            // it now greets you by name and by the hour, the
                            // same way the web's welcome does.
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (welcomeName.isBlank()) greetingLine() else "${greetingLine()},",
                                    color = Color(0xFF352742),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                if (welcomeName.isNotBlank()) {
                                    Text(
                                        " $welcomeName",
                                        color = Color(0xFF703BE2),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                                Text(
                                    " 👋",
                                    color = Color(0xFF352742),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            Text(
                                "Your Personal AI Assistant",
                                modifier = Modifier.padding(top = 4.dp),
                                color = Color(0xFF81758D),
                                fontSize = 12.sp,
                            )
                            // The panel carries the purple, with a 3dp accent
                            // down its left edge - the same panel the web draws.
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFF8F2FF),
                                border = BorderStroke(1.dp, Color(0xFFE2D5F8)),
                            ) {
                                Row(Modifier.height(IntrinsicSize.Min)) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(
                                                androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(
                                                        Color(0xFF703BE2),
                                                        Color(0xFFB78BFF),
                                                    ),
                                                ),
                                            ),
                                    )
                                    Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
                                        Text(
                                            "What shall we do?",
                                            color = Color(0xFF352742),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            if (welcomeMessageIndex == 0) {
                                                "I can read a screenshot, plan a trip, draft a message or explain something dense."
                                            } else {
                                                "Ask me anything."
                                            },
                                            modifier = Modifier.padding(top = 3.dp),
                                            color = Color(0xFF6F6578),
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                QuickPromptChip("Explain this") { input = "Explain this" }
                                QuickPromptChip("Create image") { input = "Create image" }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                QuickPromptChip("Help me plan") { input = "Help me plan" }
                                QuickPromptChip("More…") { input = "Tell me more" }
                            }

                            // Pick up where you left off.
                            if (recentChats.isNotEmpty()) {
                                Text(
                                    "PICK UP WHERE YOU LEFT OFF",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 7.dp),
                                    color = Color(0xFF9A929F),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                                recentChats.forEach { chat ->
                                    RecentChatRow(
                                        title = chat.title,
                                        whenLabel = chatWhenLabel(chat.updatedAtMillis),
                                        onClick = { openWelcomeConversation(chat.id) },
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(messages) { index, message ->
                    if (message.fromUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Column(
                                modifier = Modifier.widthIn(max = 260.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (message.attachments.isNotEmpty()) {
                                    SentAttachmentContent(message.attachments)
                                }
                                if (message.text.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = 18.dp,
                                            bottomEnd = 5.dp,
                                        ),
                                        color = Color(0xFF703BE2),
                                    ) {
                                        Text(
                                            message.text,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            color = Color.White,
                                            fontSize = 15.sp,
                                        )
                                    }
                                    MessageActions(
                                        text = message.text,
                                        alignEnd = true,
                                        // Editing replaces the message: the thread is cut
                                        // back to just before it, so the old prompt and the
                                        // answer it got are gone and the edited text takes
                                        // their place instead of being appended below them.
                                        onEdit = {
                                            messages = messages.take(index)
                                            input = message.text
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            IndooneHomeLogo(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(24.dp)
                                    .align(Alignment.Bottom),
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = 5.dp,
                                        bottomEnd = 18.dp,
                                    ),
                                    color = Color(0xFFF5F2FB),
                                ) {
                                    Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                                        // A reply that carries links reads better as its prose and
                                        // then one row per link, each with a copy button, than as
                                        // one run of text with the URLs buried inside it.
                                        val parts = remember(message.text) { splitReplyIntoParts(message.text) }
                                        parts.forEachIndexed { index, part ->
                                            val gap = if (index == 0) 0.dp else 8.dp
                                            when (part) {
                                                is ReplyPart.Prose -> Text(
                                                    part.text,
                                                    Modifier.padding(top = gap),
                                                    color = Color(0xFF292331),
                                                    fontSize = 15.sp,
                                                )
                                                is ReplyPart.Link -> if (part.label.isNotBlank()) {
                                                    MarkdownLink(
                                                        label = part.label,
                                                        url = part.url,
                                                        modifier = Modifier.padding(top = gap),
                                                    )
                                                } else {
                                                    ReplyLinkRow(
                                                        url = part.url,
                                                        modifier = Modifier.padding(top = gap),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                MessageActions(
                                    text = message.text,
                                    assistant = true,
                                    speaking = speakingText == message.text,
                                    pairText = run {
                                        // The question this reply answered, so copy and
                                        // share hand over the exchange rather than an
                                        // answer that means nothing on its own.
                                        var asked = ""

                                        for (position in index - 1 downTo 0) {
                                            if (messages[position].fromUser) {
                                                asked = messages[position].text.trim()
                                                break
                                            }
                                        }

                                        if (asked.isBlank()) {
                                            message.text
                                        } else {
                                            "User: $asked\n\nAI: ${message.text}"
                                        }
                                    },
                                    initialRating = remember(message.text) {
                                        MessageFeedbackStore.ratingFor(context, message.text)
                                    },
                                    onListen = { speakOrStop(message.text) },
                                    onRate = { kind ->
                                        MessageFeedbackStore.record(context, message.text, kind)
                                    },
                                    onRetry = if (index == messages.indexOfLast { !it.fromUser } && retriesAvailable(messages)) {
                                        {
                                            // Re-ask the question behind this reply. The
                                            // trailing exchange comes off first - the reply
                                            // and the question it answered - because
                                            // sendMessage adds the question back, and both
                                            // are in the thread already.
                                            var question = ""

                                            for (position in messages.indices.reversed()) {
                                                if (messages[position].fromUser) {
                                                    question = messages[position].text.trim()
                                                    break
                                                }
                                            }

                                            if (question.isNotEmpty()) {
                                                val trimmed = messages.toMutableList()

                                                while (
                                                    trimmed.isNotEmpty() &&
                                                    !trimmed.last().fromUser
                                                ) {
                                                    trimmed.removeAt(trimmed.size - 1)
                                                }

                                                if (trimmed.isNotEmpty()) {
                                                    trimmed.removeAt(trimmed.size - 1)
                                                }

                                                messages = trimmed
                                                input = question
                                                // A stale flag would swallow the retry
                                                // silently - the tap would do nothing.
                                                isSending = false
                                                sendMessage()
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                )
                                if (message.chips.isNotEmpty()) {
                                    // Scrolls sideways: a reply can offer three or four
                                    // follow-ups, and wrapping them would push the
                                    // conversation down for something that is optional.
                                    Row(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        message.chips.forEach { chip ->
                                            Text(
                                                text = chip.label,
                                                color = Color(0xFF4B21B3),
                                                fontSize = 13.sp,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(Color(0xFFF4F2FF))
                                                    .clickable {
                                                        input = chip.action
                                                        sendMessage()
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (chatActivityState != ChatActivityState.IDLE) {
                    item(key = "indoone-chat-status") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            IndooneHomeLogo(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(24.dp)
                                    .align(Alignment.Bottom),
                            )
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = 5.dp,
                                    bottomEnd = 18.dp,
                                ),
                                color = Color(0xFFF5F2FB),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color(0xFF703BE2),
                                        strokeWidth = 1.8.dp,
                                    )
                                    Text(
                                        chatActivityState.label(),
                                        color = Color(0xFF6F6578),
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }

            }

            if (isPlusMenuOpen) {
                PlusAttachmentSheet(
                    onDismiss = { isPlusMenuOpen = false },
                    onCamera = {
                        isPlusMenuOpen = false
                        val cameraPermissionGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (cameraPermissionGranted) {
                            launchCameraCapture()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onPhotos = {
                        isPlusMenuOpen = false
                        if (pendingAttachments.size >= MAX_CHAT_ATTACHMENTS) {
                            messages = messages + ChatMessage("You can attach up to 10 files per message.", false)
                        } else {
                            photosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    },
                    onFiles = {
                        isPlusMenuOpen = false
                        if (pendingAttachments.size >= MAX_CHAT_ATTACHMENTS) {
                            messages = messages + ChatMessage("You can attach up to 10 files per message.", false)
                        } else {
                            filesLauncher.launch(arrayOf("*/*"))
                        }
                    },
                )
            }
            }

            if (pendingAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pendingAttachments.forEach { attachment ->
                        PendingAttachmentPreview(
                            attachment = attachment,
                            enabled = !isSending,
                            onRemove = {
                                if (!isSending) {
                                    val cameraFileName = attachment.uri
                                        ?.takeIf { it.authority == "${context.packageName}.fileprovider" }
                                        ?.lastPathSegment
                                        ?.takeIf { it.startsWith("indoone-camera-") && it.endsWith(".jpg") }
                                    cameraFileName?.let { File(File(context.cacheDir, "camera"), it).delete() }
                                    pendingAttachments = pendingAttachments.filterNot { it.id == attachment.id }
                                }
                            },
                        )
                    }
                }
            }


            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.padding(end = 8.dp).size(42.dp).clip(CircleShape).background(Color(0xFFF5F2FB)).clickable(enabled = !isSending && !isAddingAttachments) {
                        isPlusMenuOpen = true
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Color(0xFF703BE2), fontSize = 25.sp, fontWeight = FontWeight.Medium) }

                Surface(
                    modifier = Modifier.weight(1f).pointerInput(isPlusMenuOpen) {
                        if (isPlusMenuOpen) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    // Watch the tap without consuming it, so the text field can
                                    // still gain focus and open the keyboard normally.
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        break
                                    }
                                    if (change.previousPressed && !change.pressed) {
                                        isPlusMenuOpen = false
                                        break
                                    }
                                    if (!change.pressed) break
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFAF9FC),
                    border = BorderStroke(1.dp, Color(0xFFE5E0ED)),
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        // The composer grows with the draft, up to 120dp, and then scrolls.
                        // The web's textarea does exactly this - autoGrow() in chat-home.js
                        // sets its height to min(scrollHeight, 120) on every keystroke, and
                        // home.css caps it at max-height: 120px. This field used to be
                        // singleLine, so a long draft scrolled sideways on one line and you
                        // could not read what you had typed. No singleLine means the text
                        // wraps, which is what makes it grow.
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 13.dp)
                            .heightIn(max = 120.dp),
                        // Keep the composer available while an AI reply is in progress.
                        // The send action remains locked until the current reply finishes.
                        enabled = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF17151D), fontSize = 15.sp),
                        decorationBox = { innerTextField -> Box { if (input.isEmpty()) Text("Message Indoone AI", color = Color(0xFF8A8492), fontSize = 15.sp); innerTextField() } },
                    )
                }

                val hasPendingAttachment = pendingAttachments.isNotEmpty()
                val showSendButton = input.isNotBlank() || hasPendingAttachment
                val canSend = showSendButton && !isSending && !isAddingAttachments && !isListening

                // Keep the microphone available even after text is typed. Voice input
                // can append to the draft, while Send appears alongside the microphone.
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isListening) Color(0xFF703BE2) else Color(0xFFF5F2FB))
                        .clickable(enabled = !isSending) {
                            isPlusMenuOpen = false
                            onVoiceButtonClick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = if (isListening) "Stop voice input" else "Voice input",
                        tint = if (isListening) Color.White else Color(0xFF4A4650),
                        modifier = Modifier.size(21.dp),
                    )
                }

                if (showSendButton) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (canSend) Color(0xFF703BE2) else Color(0xFFE7E3EC))
                            .clickable(enabled = canSend, onClick = ::sendMessage),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.2.dp,
                            )
                        } else {
                            Text(
                                "➤",
                                color = if (canSend) Color.White else Color(0xFF9B95A3),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            voiceError?.let { error ->
                Text(
                    error,
                    color = Color(0xFFB42318),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                )
            }

            if (isListening) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF5F2FB),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF703BE2)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            "Listening… tap mic to stop",
                            modifier = Modifier.padding(start = 10.dp),
                            color = Color(0xFF4A4650),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            AppBottomNav(activeTab = AppTab.HOME, onAccountsClick = {}, onLobbyClick = onLobbyClick, onVibeClick = onVibeClick, onSettingsClick = onSettingsClick)
        }
    }
}

private sealed interface ReplyPart {
    data class Prose(val text: String) : ReplyPart
    data class Link(val url: String, val label: String = "") : ReplyPart
}

private val REPLY_LINK_PATTERN = Regex("""https?://\S+""")

// [label](url) is a link the backend wrote on purpose - a repository list, for example.
private val MARKDOWN_LINK_PATTERN = Regex("""\[([^\]\n]+)\]\((https?://[^\s)]+)\)""")

/**
 * Splits a reply into prose and links, so each link can be given a row of its own.
 *
 * Blank prose is dropped: a reply that is nothing but a link should not leave an empty
 * line above it, and prose that was only the whitespace around a link should not become
 * a line of its own.
 */
private fun splitReplyIntoParts(text: String): List<ReplyPart> {
    val parts = mutableListOf<ReplyPart>()
    var cursor = 0

    // A markdown link is matched first, so its label stays inline with the line the
    // backend wrote instead of the URL being lifted out into a row of its own.
    MARKDOWN_LINK_PATTERN.findAll(text).forEach { match ->
        appendBareLinks(parts, text.substring(cursor, match.range.first))
        parts += ReplyPart.Link(url = match.groupValues[2], label = match.groupValues[1])
        cursor = match.range.last + 1
    }

    appendBareLinks(parts, text.substring(cursor))
    return parts
}

// A bare URL in the prose around a markdown link keeps its own row, as before.
private fun appendBareLinks(parts: MutableList<ReplyPart>, segment: String) {
    var cursor = 0

    REPLY_LINK_PATTERN.findAll(segment).forEach { match ->
        val prose = segment.substring(cursor, match.range.first).trim()
        if (prose.isNotEmpty()) parts += ReplyPart.Prose(prose)
        parts += ReplyPart.Link(match.value)
        cursor = match.range.last + 1
    }

    val tail = segment.substring(cursor).trim()
    if (tail.isNotEmpty()) parts += ReplyPart.Prose(tail)
}

/**
 * One link on its own row, with a copy button at the end of that row. The button puts
 * the whole URL on the clipboard and says so, because a tap with no visible result
 * reads as though it did nothing.
 */
// Copy and Share under a bubble.
//
// Quiet on purpose: they are useful, not the point of the screen, and a row of buttons
// that shouts under every message makes a conversation hard to read. Share opens the
// system chooser, so the message can go to WhatsApp or Notes without the app knowing
// anything about where it went.
@Composable
private fun MessageActions(
    text: String,
    alignEnd: Boolean = false,
    assistant: Boolean = false,
    speaking: Boolean = false,
    initialRating: String = "",
    pairText: String = "",
    onListen: (() -> Unit)? = null,
    onRate: ((String) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val value = text.trim()

    if (value.isEmpty()) {
        return
    }

    // Copy and share carry the question with the answer: an answer on its own is usually
    // meaningless out of context, and pasting a pair back into a chat is what people
    // actually do with it.
    val pair = pairText.trim().ifEmpty { value }

    // The chosen thumb stays coloured after the tap, so the state is visible - a rating
    // that looks identical before and after reads as a button that did nothing.
    var rating by remember(value, initialRating) { mutableStateOf(initialRating) }

    Row(
        modifier = Modifier.padding(start = if (alignEnd) 0.dp else 2.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!assistant) {
            // A sent message carries one action: edit. Editing means putting the text back
            // where it came from - the composer - rather than rewriting history, because
            // the reply below it answered that text.
            IconAction(
                icon = Icons.Outlined.Edit,
                label = "Edit this message",
            ) { onEdit?.invoke() }
        } else {
            IconAction(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy this exchange",
            ) {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Indoone exchange", pair))
                // A tap with no visible result reads as a broken button.
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }

            IconAction(
                icon = Icons.Outlined.Share,
                label = "Share this exchange",
            ) {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, pair)
                }
                context.startActivity(Intent.createChooser(share, "Share exchange"))
            }

            if (onListen != null) {
                IconAction(
                    icon = if (speaking) Icons.Outlined.Stop else Icons.Outlined.VolumeUp,
                    label = if (speaking) "Stop reading" else "Listen to this reply",
                ) { onListen() }
            }

            if (onRate != null) {
                IconAction(
                    icon = Icons.Outlined.ThumbUp,
                    label = "Good reply",
                    tint = if (rating == MessageFeedbackStore.UP) Color(0xFF6330DB) else null,
                ) {
                    rating = MessageFeedbackStore.UP
                    onRate(MessageFeedbackStore.UP)
                }

                IconAction(
                    icon = Icons.Outlined.ThumbDown,
                    label = "Bad reply",
                    tint = if (rating == MessageFeedbackStore.DOWN) Color(0xFF6330DB) else null,
                ) {
                    rating = MessageFeedbackStore.DOWN
                    onRate(MessageFeedbackStore.DOWN)
                }
            }

            if (onRetry != null) {
                IconAction(
                    icon = Icons.Outlined.Refresh,
                    label = "Ask again",
                ) { onRetry() }
            }
        }
    }
}

// Retry is limited to two regenerations: the answer, then two more attempts. After that
// the thread holds three answers to one question, which is enough to know whether it will
// ever be right - and an unlimited retry is just a slot machine.
private fun retriesAvailable(messages: List<ChatMessage>): Boolean {
    val lastQuestion = messages.indexOfLast { it.fromUser }

    if (lastQuestion < 0) {
        return false
    }

    return messages.drop(lastQuestion + 1).count { !it.fromUser } <= 2
}

// One icon button in a bubble's action row. 30dp is the icon's box, not the touch
// target: Compose's minimum touch target expands it, so a thumb still lands on it.
@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint ?: Color(0xFF8A8F98),
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(7.dp),
    )
}

// A [label](url) link. The device's browser, not an in-app webview: the user asked for
// a link, and the page it opens is not ours to render.
@Composable
private fun MarkdownLink(label: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Text(
        text = label,
        modifier = modifier.clickable {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        },
        color = Color(0xFF4B21B3),
        fontSize = 15.sp,
    )
}

@Composable
private fun ReplyLinkRow(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.widthIn(max = 250.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE6DEF4)),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                url,
                modifier = Modifier.weight(1f, fill = false),
                color = Color(0xFF6330DB),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFF3EEFC))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Indoone link", url))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy link",
                    tint = Color(0xFF6330DB),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
