package com.indoone.notifications

enum class NotificationCategory(
    val id: String,
    val title: String,
    val description: String,
) {
    SECURITY("security", "Security", "Important account and security alerts"),
    AI("ai", "AI & Chat", "AI, chat and background task updates"),
    VIBE("vibe", "Vibe", "Vibe voice session updates"),
    ACCOUNTS("accounts", "Accounts", "Authenticator account and code-related updates"),
    INTEGRATIONS("integrations", "Integrations", "Connected plugins and services"),
    UPDATES("updates", "Updates", "Sankalp app and service updates"),
}
