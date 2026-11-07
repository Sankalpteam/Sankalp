package com.indoone.menu.security

import androidx.compose.runtime.Composable
import com.indoone.menu.legal.LegalWebScreen

/**
 * Security - shown from the web page, not written again here.
 *
 * Four points about how an account is created, signed into and protected. Same reason as the
 * other three information screens: written once, in web/menu/security, and generated for the
 * app from there.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    onAccountsClick: () -> Unit = {},
    onLobbyClick: () -> Unit = {},
    onVibeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    LegalWebScreen(
        title = "Security",
        path = "/legal/security.html",
        assetFile = "legal/security.html",
        onBack = onBack,
    )
}
