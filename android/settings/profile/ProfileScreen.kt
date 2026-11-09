package com.indoone.settings.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indoone.menu.AppChevron
import com.indoone.menu.plugins.email.gmail.GmailPluginApi
import com.indoone.settings.SettingsScreen

private enum class ProfileSheet {
    NONE,
    MOBILE,
    EMAIL,
}

@Composable
fun ProfileScreen(
    state: ProfileState,
    profileViewModel: ProfileViewModel = viewModel(),
    onMenuClick: () -> Unit = {},
    onBack: () -> Unit,
    onMobileClick: () -> Unit,
    onEmailClick: () -> Unit,
    onPluginsClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onLobbyClick: () -> Unit,
    onVibeClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var sheet by remember { mutableStateOf(ProfileSheet.NONE) }
    // These hold the NEW values the person types, not the ones on the account. The
    // saved values stay in state and are only shown, read-only. Keying on the sheet
    // clears both fields every time a sheet is opened.
    var mobile by remember(sheet) { mutableStateOf("") }
    var email by remember(sheet) { mutableStateOf("") }
    var password by remember(sheet) { mutableStateOf("") }
    var gmailConnectionSummary by remember { mutableStateOf("Checking saved connections…") }

    LaunchedEffect(Unit) {
        runCatching { GmailPluginApi.getConnectionStatus() }
            .onSuccess { status ->
                gmailConnectionSummary = when {
                    status.connected && !status.emailAddress.isNullOrBlank() ->
                        "Gmail linked · ${status.emailAddress}"
                    status.connected -> "Gmail linked to this Sankalp profile"
                    else -> "Connect Gmail and manage your plugin connections"
                }
            }
            .onFailure {
                gmailConnectionSummary = "Could not load saved connections"
            }
    }

    Box(Modifier.fillMaxSize()) {
        SettingsScreen(
            onMenuClick = onMenuClick,
            onProfileClick = {},
            onAccountsClick = onAccountsClick,
            onLobbyClick = onLobbyClick,
            onVibeClick = onVibeClick,
            onSettingsClick = onSettingsClick,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x5519141F))
                .clickable(onClick = if (sheet == ProfileSheet.NONE) onBack else { { sheet = ProfileSheet.NONE } }),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (sheet == ProfileSheet.NONE) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 430.dp)
                        .wrapContentHeight()
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp)
                        .clickable(onClick = {}),
                    shape = RoundedCornerShape(25.dp),
                    color = Color.White,
                    shadowElevation = 14.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 19.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Profile",
                                color = Color(0xFF17151D),
                                fontSize = 21.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                modifier = Modifier
                                    .size(35.dp)
                                    .clickable(onClick = onBack),
                                shape = RoundedCornerShape(11.dp),
                                color = Color(0xFFF5F2F8),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("×", color = Color(0xFF242129), fontSize = 21.sp, lineHeight = 21.sp)
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFEEE8F5), RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFAF8FD),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = Color(0xFFEEE6FF),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "P",
                                            color = Color(0xFF6330DB),
                                            fontSize = 16.sp,
                                            lineHeight = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        state.email,
                                        color = Color(0xFF17151D),
                                        fontSize = 15.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        state.mobile,
                                        modifier = Modifier.padding(top = 4.dp),
                                        color = Color(0xFF77707F),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        ProfileActionRow(
                            title = "Change mobile number",
                            description = "Update your verified phone number",
                            onClick = { sheet = ProfileSheet.MOBILE },
                        )
                        Spacer(Modifier.height(10.dp))
                        ProfileActionRow(
                            title = "Change email",
                            description = "Update your account email address",
                            onClick = { sheet = ProfileSheet.EMAIL },
                        )
                        Spacer(Modifier.height(10.dp))
                        ProfileActionRow(
                            title = "Connections & plugins",
                            description = gmailConnectionSummary,
                            onClick = onPluginsClick,
                        )

                        state.error?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(top = 14.dp),
                                color = Color(0xFFD93025),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        state.message?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(top = 14.dp),
                                color = Color(0xFF6330DB),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            } else {
                when (sheet) {
                ProfileSheet.MOBILE -> {
                    ChangeMobileSheet(
                        currentMobile = state.mobile,
                        newMobile = mobile,
                        password = password,
                        busy = state.busy,
                        error = state.error,
                        message = state.message,
                        onNewMobileChange = { mobile = it },
                        onPasswordChange = { password = it },
                        onClose = { sheet = ProfileSheet.NONE },
                        onSave = { profileViewModel.updateMobile(mobile, password) },
                    )
                }
                ProfileSheet.EMAIL -> {
                    ChangeEmailSheet(
                        currentEmail = state.email,
                        newEmail = email,
                        password = password,
                        busy = state.busy,
                        error = state.error,
                        message = state.message,
                        onNewEmailChange = { email = it },
                        onPasswordChange = { password = it },
                        onClose = { sheet = ProfileSheet.NONE },
                        onSave = { profileViewModel.updateEmail(email, password) },
                    )
                }
                ProfileSheet.NONE -> Unit
            }
            }
        }
    }
}

@Composable
private fun ChangeMobileSheet(
    currentMobile: String,
    newMobile: String,
    password: String,
    busy: Boolean,
    error: String?,
    message: String?,
    onNewMobileChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    ProfileFormSheet(
        title = "Change mobile number",
        description = "The number on your Sankalp account is shown below. Enter the new number and your current password to confirm.",
        onClose = onClose,
    ) {
        SheetReadOnlyValue("CURRENT MOBILE NUMBER", currentMobile)
        Spacer(Modifier.height(12.dp))
        SheetFieldLabel("ENTER YOUR NEW MOBILE NUMBER")
        SheetTextField(newMobile, onNewMobileChange, placeholder = "+91 98765 43210")
        Spacer(Modifier.height(12.dp))
        SheetFieldLabel("CURRENT PASSWORD")
        SheetTextField(password, onPasswordChange, placeholder = "Enter your current password", password = true)
        Spacer(Modifier.height(24.dp))
        SheetGradientButton(if (busy) "Updating…" else "Update mobile number", enabled = !busy, onClick = onSave)
        FormFeedback(error, message)
    }
}

@Composable
private fun ChangeEmailSheet(
    currentEmail: String,
    newEmail: String,
    password: String,
    busy: Boolean,
    error: String?,
    message: String?,
    onNewEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    ProfileFormSheet(
        title = "Change email",
        description = "The email on your Sankalp account is shown below. Enter the new email and your current password to confirm.",
        onClose = onClose,
    ) {
        SheetReadOnlyValue("CURRENT EMAIL", currentEmail)
        Spacer(Modifier.height(12.dp))
        SheetFieldLabel("ENTER YOUR NEW EMAIL")
        SheetTextField(newEmail, onNewEmailChange, placeholder = "you@example.com")
        Spacer(Modifier.height(12.dp))
        SheetFieldLabel("CURRENT PASSWORD")
        SheetTextField(password, onPasswordChange, placeholder = "Enter your current password", password = true)
        Spacer(Modifier.height(24.dp))
        SheetGradientButton(if (busy) "Changing…" else "Change email", enabled = !busy, onClick = onSave)
        FormFeedback(error, message)
    }
}

/**
 * A value that is only shown, never edited. The saved mobile number and email used to
 * be typed straight into the input, which read as though they were up for editing.
 */
@Composable
private fun SheetReadOnlyValue(label: String, value: String) {
    SheetFieldLabel(label)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(41.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1EEF6))
            .border(1.dp, Color(0xFFE4DDEA), RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            value.ifBlank { "Not set" },
            color = Color(0xFF625D68),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ProfileFormSheet(
    title: String,
    description: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .clickable(onClick = {}),
        shape = RoundedCornerShape(25.dp),
        color = Color.White,
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 19.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, color = Color(0xFF17151D), fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.size(35.dp).clickable(onClick = onClose),
                    shape = RoundedCornerShape(11.dp),
                    color = Color(0xFFF5F2F8),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("×", color = Color(0xFF242129), fontSize = 21.sp, lineHeight = 21.sp)
                    }
                }
            }
            Text(
                description,
                modifier = Modifier.padding(top = 17.dp, bottom = 14.dp),
                color = Color(0xFF77707F),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            content()
        }
    }
}

@Composable
private fun SheetFieldLabel(text: String) {
    Text(text, color = Color(0xFF625D68), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp))
}

@Composable
private fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    password: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(41.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFAF8FD))
            .border(1.dp, Color(0xFFE4DDEA), RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF2C2733), fontSize = 12.sp, lineHeight = 16.sp),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            cursorBrush = SolidColor(Color(0xFF6330DB)),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = Color(0xFF9A92A1), fontSize = 12.sp, lineHeight = 16.sp)
                }
                inner()
            },
        )
    }
}

@Composable
private fun SheetGradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Color(0xFF632FE5), Color(0xFF9648EE)))
                else SolidColor(Color(0xFFB9A9D3)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FormFeedback(error: String?, message: String?) {
    error?.let {
        Text(it, Modifier.padding(top = 12.dp), color = Color(0xFFD93025), fontSize = 11.sp)
    }
    message?.let {
        Text(it, Modifier.padding(top = 12.dp), color = Color(0xFF6330DB), fontSize = 11.sp)
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFECE9F0)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color(0xFF2C2733),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color(0xFF8A8392),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            AppChevron()
        }
    }
}
