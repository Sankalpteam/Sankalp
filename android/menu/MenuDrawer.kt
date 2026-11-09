package com.indoone.menu

import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Apps
import com.indoone.MainActivity
import com.indoone.lobby.accounts.storage.AccountRepositoryProvider
import com.indoone.menu.about.MenuAboutScreen
import com.indoone.menu.privacypolicy.PrivacyPolicyScreen
import com.indoone.menu.security.SecurityScreen
import com.indoone.menu.termsofuse.TermsOfUseScreen
import com.indoone.menu.trash.TrashScreen
import com.indoone.menu.profile.ProfileMenuScreen
import com.indoone.menu.memory.MemoryMenuScreen
import com.indoone.menu.plugins.PluginsMenuScreen
import com.indoone.menu.plugins.zoho_mail.ZohoMailPluginMenuScreen
import com.indoone.menu.chathistory.ChatHistoryMenuScreen
import com.indoone.menu.dataprivacy.DataPrivacyMenuScreen
import com.indoone.settings.applock.AppLockStore
import com.indoone.settings.unlock.UnlockAppActivity

@Composable
fun MenuDrawer(
    onDismiss: () -> Unit,
    onAccounts: () -> Unit,
    onFavorites: () -> Unit,
    onTrash: () -> Unit,
    onSecurity: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    onLock: () -> Unit,
    onDangerZone: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { AccountRepositoryProvider(context.applicationContext) }
    var showTerms by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSetLockPrompt by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showChatHistory by remember { mutableStateOf(false) }
    var showDataPrivacy by remember { mutableStateOf(false) }
    var showPlugins by remember { mutableStateOf(false) }
    var showZohoMailPlugin by remember { mutableStateOf(false) }

    if (showZohoMailPlugin) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            ZohoMailPluginMenuScreen(
                onBack = {
                    showZohoMailPlugin = false
                    showPlugins = true
                },
            )
        }
        return
    }

    if (showPlugins) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            PluginsMenuScreen(
                onBack = {
                    showPlugins = false
                },
            )
        }
        return
    }

    if (showProfile) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            ProfileMenuScreen(onBack = { showProfile = false; onDismiss() })
        }
        return
    }
    if (showMemory) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            MemoryMenuScreen(onBack = { showMemory = false; onDismiss() })
        }
        return
    }
    if (showChatHistory) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            ChatHistoryMenuScreen(
                onBack = { showChatHistory = false; onDismiss() },
                onOpenChat = { conversationId ->
                    context.startActivity(
                        Intent(context, MainActivity::class.java).apply {
                            putExtra("open_conversation_id", conversationId)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        },
                    )
                },
            )
        }
        return
    }
    if (showDataPrivacy) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            DataPrivacyMenuScreen(onBack = { showDataPrivacy = false; onDismiss() })
        }
        return
    }
    if (showAbout) {
        MenuAboutScreen(onBack = onDismiss, onAccountsClick = onAccounts, onLobbyClick = onDismiss, onVibeClick = onDismiss, onSettingsClick = onDismiss)
        return
    }
    if (showTerms) {
        TermsOfUseScreen(onBack = onDismiss, onAccountsClick = onAccounts, onLobbyClick = onDismiss, onVibeClick = onDismiss, onSettingsClick = onDismiss)
        return
    }
    if (showTrash) {
        TrashScreen(repository = repository, onBack = onDismiss, onAccountsClick = onAccounts, onLobbyClick = onDismiss, onVibeClick = onDismiss, onSettingsClick = onDismiss)
        return
    }
    if (showSecurity) {
        SecurityScreen(onBack = onDismiss, onAccountsClick = onAccounts, onLobbyClick = onDismiss, onVibeClick = onDismiss, onSettingsClick = onDismiss)
        return
    }
    if (showPrivacy) {
        PrivacyPolicyScreen(onBack = onDismiss, onAccountsClick = onAccounts, onLobbyClick = onDismiss, onVibeClick = onDismiss, onSettingsClick = onDismiss)
        return
    }

    if (showSetLockPrompt) {
        AlertDialog(
            onDismissRequest = { showSetLockPrompt = false },
            title = { Text("Set your App Lock") },
            text = {
                Text("Sankalp is not locked yet. Set an App PIN, and Lock App will ask for it, or for your fingerprint, before the app opens.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSetLockPrompt = false
                    onDismiss()
                    onLock()
                }) { Text("Set App Lock") }
            },
            dismissButton = {
                TextButton(onClick = { showSetLockPrompt = false }) { Text("Cancel") }
            },
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            color = Color(0xFFF7F3FC),
            shadowElevation = 22.dp,
            shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFAF8FF),
                                Color(0xFFF2ECFA),
                                Color(0xFFF8F4FC),
                            ),
                        ),
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 15.dp, vertical = 19.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 5.dp, end = 0.dp, top = 3.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Keep Sankalp's existing logo and wordmark unchanged; no avatar is added.
                    IndooneMenuLogo(modifier = Modifier.size(36.dp))
                    Text(
                        "Sankalp",
                        modifier = Modifier.padding(start = 9.dp).weight(1f),
                        color = Color(0xFF263753),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close menu",
                            tint = Color(0xFF59677D),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                    color = Color(0xFFE8DDF4),
                    thickness = 1.dp,
                )

                DrawerItem(
                    icon = Icons.Outlined.AddComment,
                    title = "New Chat",
                    trailing = null,
                    highlighted = true,
                ) {
                    context.startActivity(
                        Intent(context, MainActivity::class.java).apply {
                            putExtra("start_new_chat", true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        },
                    )
                }

                DrawerSectionLabel("PERSONAL")
                DrawerItem(Icons.Outlined.PersonOutline, "Profile", null) { showProfile = true }
                DrawerItem(Icons.Outlined.Memory, "Memory", null) { showMemory = true }
                DrawerItem(Icons.Outlined.Apps, "Plugins", null, showChevron = true) { showPlugins = true }
                DrawerItem(Icons.Outlined.History, "Chat History", null) { showChatHistory = true }
                DrawerItem(Icons.Outlined.PrivacyTip, "Data & Privacy", null) { showDataPrivacy = true }

                DrawerSectionLabel("ACCOUNTS")
                DrawerItem(Icons.Outlined.StarBorder, "Favorites", null, onClick = onFavorites)
                DrawerItem(Icons.Outlined.DeleteOutline, "Trash", null) { showTrash = true }

                DrawerSectionLabel("SECURITY & INFORMATION")
                DrawerItem(Icons.Outlined.Security, "Security", null) { showSecurity = true }
                DrawerItem(Icons.Outlined.Description, "Terms of Use", null) { showTerms = true }
                DrawerItem(Icons.Outlined.Visibility, "Privacy Policy", null) { showPrivacy = true }
                DrawerItem(Icons.Outlined.Info, "About Sankalp", null) { showAbout = true }
                DrawerItem(Icons.Outlined.Lock, "Lock App", null) {
                    if (AppLockStore(context).isEnabled()) {
                        onDismiss()
                        context.startActivity(Intent(context, UnlockAppActivity::class.java))
                    } else {
                        showSetLockPrompt = true
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF101827).copy(alpha = 0.30f))
                .clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun IndooneMenuLogo(modifier: Modifier = Modifier) {
    // One logo, one definition: this used to be a third copy of the old diamond.
    IndooneTopBarLogo(modifier = modifier)
}

@Composable
private fun DrawerSectionLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(start = 9.dp, top = 15.dp, bottom = 5.dp),
        color = Color(0xFF8592A8),
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp,
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    trailing: String?,
    highlighted: Boolean = false,
    showChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    val iconColor = if (highlighted) Color.White else Color(0xFF51627D)
    val labelColor = if (highlighted) Color(0xFF6E35C8) else Color(0xFF34445F)
    val rowBackground = if (highlighted) Color(0xFFEDE3FA) else Color.Transparent
    val iconBackground = if (highlighted) {
        Brush.linearGradient(listOf(Color(0xFF9A68E8), Color(0xFF6F35C5)))
    } else {
        Brush.linearGradient(listOf(Color.White, Color(0xFFF8F4FC)))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(rowBackground)
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            shadowElevation = if (highlighted) 3.dp else 0.dp,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconColor,
                )
            }
        }

        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(start = 11.dp),
            color = labelColor,
            fontSize = 13.sp,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
            lineHeight = 17.sp,
        )

        if (trailing != null) {
            Surface(
                color = Color(0xFFECE2F7),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = trailing,
                    color = Color(0xFF6B3FA5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        } else if (showChevron) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Open $title",
                tint = Color(0xFF8B9AB0),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
