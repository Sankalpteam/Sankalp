package com.indoone.menu.privacypolicy

import androidx.compose.runtime.Composable
import com.indoone.menu.legal.LegalWebScreen

/**
 * Privacy Policy - shown from the web page, not written again here.
 *
 * The text used to live in this file as Kotlin strings, and it drifted from the web copy: the
 * policy gained seven sections about the assistant on the web and this screen kept showing the
 * old thirteen. One document, one place. The web page is that place, and a snapshot bundled in
 * the APK covers the case where it cannot be fetched.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    onAccountsClick: () -> Unit = {},
    onLobbyClick: () -> Unit = {},
    onVibeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {

    LegalWebScreen(
        title = "Privacy Policy",
        path = "/legal/privacy-policy.html",
        assetFile = "legal/privacy-policy.html",
        onBack = onBack,
    )
}
