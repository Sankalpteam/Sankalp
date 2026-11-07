package com.indoone.menu.dataprivacy

import androidx.compose.runtime.Composable
import com.indoone.menu.legal.LegalWebScreen

/**
 * Data & Privacy - shown from the web page, not written again here.
 *
 * The text used to live in this file as Kotlin strings, and it drifted from the web copy the
 * same way the privacy policy did. One document, one place: web/menu/data-privacy, and a
 * snapshot bundled in the APK for when the page cannot be fetched.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DataPrivacyMenuScreen(
    onBack: () -> Unit,
) {
    LegalWebScreen(
        title = "Data & Privacy",
        path = "/legal/data-privacy.html",
        assetFile = "legal/data-privacy.html",
        onBack = onBack,
    )
}
