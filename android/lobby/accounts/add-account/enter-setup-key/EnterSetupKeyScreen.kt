package com.indoone.lobby.accounts.addaccount.entersetupkey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.menu.AppBackButton
import com.indoone.menu.AppBottomNav
import com.indoone.menu.AppTab
import com.indoone.menu.AppTopBar

@Composable
fun EnterSetupKeyScreen(
    state: EnterSetupKeyState,
    onNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onSecretChanged: (String) -> Unit,
    onDigitsChanged: (Int) -> Unit,
    onPeriodChanged: (Int) -> Unit,
    onAlgorithmChanged: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAccountsClick: () -> Unit = onBack,
    onLobbyClick: () -> Unit = {},
    onVibeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        AppTopBar(onMenuClick = onMenuClick, onSearchClick = onSearchClick)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 110.dp),
        ) {
            AppBackButton(onClick = onBack)

            Column(modifier = Modifier.padding(top = 22.dp)) {
                Text(
                    "Enter Setup Key",
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color(0xFF17151D),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Enter the details used to generate your one-time codes.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = Color(0xFF2E2A33),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SetupTextField("ACCOUNT NAME", "e.g. Work account", state.name, onNameChanged)
                SetupTextField("EMAIL / USERNAME", "you@example.com", state.email, onEmailChanged)
                SetupTextField("SECRET KEY", "Base32 secret key", state.secret, onSecretChanged)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SetupSelect("DIGITS", state.digits.toString(), listOf("6", "8")) { onDigitsChanged(it.toInt()) }
                    SetupSelect("PERIOD", state.period.toString(), listOf("30", "60")) { onPeriodChanged(it.toInt()) }
                }

                SetupSelect("ALGORITHM", state.algorithm, listOf("SHA1", "SHA256", "SHA512"), onAlgorithmChanged)

                state.errorMessage?.let {
                    Text(it, color = Color(0xFFB3261E), fontSize = 12.sp)
                }

                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(vertical = 0.dp),
                    // Sankalp's own purple, as the Edit screen's Save now is.
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF703BE2)),
                ) {
                    Text(
                        if (state.isSaving) "Saving…" else "Save Account",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        AppBottomNav(
            activeTab = AppTab.LOBBY,
            onAccountsClick = onAccountsClick,
            onLobbyClick = onLobbyClick,
            onVibeClick = onVibeClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

@Composable
private fun SetupTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text(
            label,
            color = Color(0xFF625D68),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(Color(0xFFFBFAFC), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFE3DFE8), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 13.dp),
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFF17151D), fontSize = 13.sp),
            decorationBox = { inner ->
                if (value.isBlank()) Text(placeholder, color = Color(0xFF9B95A1), fontSize = 13.sp)
                inner()
            },
        )
    }
}

@Composable
private fun SetupSelect(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            label,
            color = Color(0xFF625D68),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(Color(0xFFFBFAFC), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE3DFE8), RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected, color = Color(0xFF17151D), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("⌄", color = Color(0xFF77717F), fontSize = 16.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}
