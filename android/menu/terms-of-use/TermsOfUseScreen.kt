package com.indoone.menu.termsofuse

import androidx.compose.runtime.Composable
import com.indoone.menu.legal.LegalWebScreen

/**
 * Terms of Use - shown from the web page, not written again here.
 *
 * Same reason as the privacy policy: the text existed twice, in Kotlin and in HTML, and only
 * one of them was ever updated. The web page is the single copy.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TermsOfUseScreen(
    onBack: () -> Unit,
    onAccountsClick: () -> Unit = {},
    onLobbyClick: () -> Unit = {},
    onVibeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {

    LegalWebScreen(
        title = "Terms of Use",
        path = "/legal/terms-of-use.html",
        assetFile = "legal/terms-of-use.html",
        onBack = onBack,
    )
}
