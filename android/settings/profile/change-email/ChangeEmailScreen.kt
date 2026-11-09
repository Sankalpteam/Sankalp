package com.indoone.settings.profile.change_email

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.menu.AppBottomNav
import com.indoone.menu.AppTab
import com.indoone.menu.AppTopBar
import com.indoone.settings.profile.ProfileState

@Composable
fun ChangeEmailScreen(
    state: ProfileState,
    onMenuClick: () -> Unit = {},
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onAccountsClick: () -> Unit,
    onLobbyClick: () -> Unit,
    onVibeClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    // The new email starts empty. The email already on the account is only shown,
    // never typed into an input.
    var newEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(onMenuClick = onMenuClick)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(18.dp),
            ) {
                Text(
                    "← Back to Profile",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                        .clickable(onClick = onBack),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Change email", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "The email on your Sankalp account is shown below. Enter the new email and your current password to confirm.",
                    modifier = Modifier.padding(top = 5.dp, bottom = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CurrentValueField("CURRENT EMAIL", state.email)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    newEmail,
                    { newEmail = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("ENTER YOUR NEW EMAIL") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    password,
                    { password = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("CURRENT PASSWORD") },
                    placeholder = { Text("Enter your current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSave(newEmail, password) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.busy) "Changing…" else "Change email") }
                state.error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                state.message?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.navigationBarsPadding())
            }
            AppBottomNav(AppTab.SETTINGS, onAccountsClick, onLobbyClick, onVibeClick, onSettingsClick)
        }
    }
}

/**
 * A value that is only shown, never edited. The email already on the account used to be
 * typed straight into the input, which read as though it were up for editing.
 */
@Composable
private fun CurrentValueField(label: String, value: String) {
    Text(label, color = Color(0xFF625D68), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1EEF6))
            .border(1.dp, Color(0xFFE4DDEA), RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            value.ifBlank { "Not set" },
            color = Color(0xFF625D68),
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
    }
}
