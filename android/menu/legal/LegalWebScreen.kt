package com.indoone.menu.legal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A legal document, shown from the web instead of being written twice.
 *
 * The policy text lives in exactly one place - the web page - and this screen reads it. Before
 * this, the same policy existed as Kotlin strings and as HTML, and the two drifted: a change
 * landed on the web and the app kept showing the old text for weeks.
 *
 * Two rules this screen holds:
 *
 *  * **It only ever loads our own host.** Any navigation elsewhere is refused, JavaScript is
 *    off, and file and content access are off. A legal page has no reason to run code.
 *  * **It works offline.** If the page cannot be fetched, a snapshot bundled in the APK is
 *    shown instead. A stale policy is bad; a policy that fails to open at all is worse.
 */
@Composable
fun LegalWebScreen(
    title: String,
    path: String,
    assetFile: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Read the snapshot once, up front: if the network is already gone by the time the
    // WebView asks, there is nothing left to fall back to.
    val offlineSnapshot = remember(assetFile) {
        runCatching {
            context.assets.open(assetFile).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C2733),
                )
            }

            // The address of the page, and a way to take it with you.
            //
            // These documents have a public address now, and someone checking them - an
            // employer, a bank, a store reviewer - usually needs the link rather than a
            // screenshot. Without this row the app showed the text and hid the one thing that
            // makes it verifiable.
            LegalAddressBar(
                title = title,
                url = LEGAL_BASE_URL + path,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    // true means "handled here, do not navigate": anything
                                    // that is not our own host is refused outright.
                                    return request.url.host !in LEGAL_HOSTS
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (!request.isForMainFrame) return

                                    if (offlineSnapshot.isNotEmpty()) {
                                        view.loadDataWithBaseURL(
                                            null,
                                            offlineSnapshot,
                                            "text/html",
                                            "utf-8",
                                            null,
                                        )
                                    }
                                }
                            }

                            loadUrl(LEGAL_BASE_URL + path)
                        }
                    },
                )
            }
        }
    }
}

/**
 * The host the document is served from, and the one it used to be served from.
 *
 * The site moved to its own subdomain, and this screen still named the old address. GitHub
 * redirects the old address to the new one, so the WebView was handed a navigation to a host
 * that was no longer in the allowlist above - and a legal page that refuses to load is worse
 * than one that is out of date. Both are accepted while the old links are still in the wild.
 */
@Composable
private fun LegalAddressBar(title: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var copied by remember(url) { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // What the page is, then where it lives. The two together are what makes a link
            // worth sending: "Privacy Policy" alone could be any page, and an address alone
            // does not say what someone is being asked to read.
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2C2733),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = url.removePrefix("https://"),
                fontSize = 11.sp,
                color = Color(0xFF6B5B86),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Sankalp document", url))
                copied = true
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = if (copied) "Link copied" else "Copy link",
                tint = Color(0xFF703BE2),
            )
        }
    }
}

private val LEGAL_HOSTS = setOf("indoone.indoworkspace.in", "sankalpteam.github.io", "indooneteam.github.io")
private const val LEGAL_BASE_URL = "https://indoone.indoworkspace.in"
