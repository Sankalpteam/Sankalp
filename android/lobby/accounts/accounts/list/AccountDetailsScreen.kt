package com.indoone.lobby.accounts.accounts.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.menu.AppBackButton
import com.indoone.menu.IndooneTopBarLogo
import com.indoone.lobby.accounts.AccountItem
import com.indoone.lobby.accounts.AccountRecord
import com.indoone.lobby.accounts.AccountsScreen
import com.indoone.lobby.accounts.AccountsState
import com.indoone.lobby.accounts.storage.AccountRepositoryProvider
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
fun AccountDetailsScreen(
    account: AccountRecord,
    code: String,
    secondsRemaining: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { AccountRepositoryProvider(context.applicationContext) }
    var backgroundAccounts by remember { mutableStateOf<List<AccountItem>>(emptyList()) }

    LaunchedEffect(repository) {
        while (true) {
            runCatching {
                repository.getAll().map { record ->
                    val now = System.currentTimeMillis()
                    val period = record.period.coerceAtLeast(1)
                    val remaining = period - ((now / 1000L) % period).toInt()
                    val generatedCode = runCatching {
                        AccountTotpGenerator.generate(
                            secret = record.secret,
                            timeMillis = now,
                            periodSeconds = period,
                            digits = record.digits,
                            algorithm = record.algorithm,
                        )
                    }.getOrDefault("------")
                    AccountItem(
                        id = record.id,
                        name = record.name,
                        email = record.email,
                        code = generatedCode,
                        secondsRemaining = remaining.coerceAtMost(period),
                        periodSeconds = period,
                        favorite = record.favorite,
                        icon = record.icon,
                        serviceClass = record.cls,
                    )
                }
            }.onSuccess { backgroundAccounts = it }
            delay(1_000L)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AccountsScreen(
            state = AccountsState(accounts = backgroundAccounts),
            onAccountClick = {},
            onAddAccount = {},
            onMenuClick = {},
            onSearchClick = {},
            onLobbyClick = {},
            onVibeClick = {},
            onSettingsClick = {},
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x5519141F))
                .clickable(onClick = onBack),
        )

        val progress = (secondsRemaining.toFloat() / account.period.coerceAtLeast(1)).coerceIn(0f, 1f)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(25.dp),
            color = Color.White,
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 21.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppBackButton(onClick = onBack)
                    Text(
                        text = account.name,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(35.dp)
                            .background(Color(0xFFF5F2F8), RoundedCornerShape(11.dp)),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit account", tint = Color(0xFF242129))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFF2ECFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The real Sankalp mark. This tile used to build its own
                        // approximation out of a "✦" character, which did not match the
                        // logo the rest of the app shows.
                        IndooneTopBarLogo(modifier = Modifier.size(34.dp))
                    }

                    Text(
                        text = account.email.ifBlank { "Authenticator account" },
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFF77717E),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = code,
                        modifier = Modifier.padding(top = 10.dp),
                        color = Color(0xFF6330DB),
                        fontSize = 43.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEEE8F7)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF7540DF)),
                        )
                    }
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = "Code expires in ${secondsRemaining.coerceAtLeast(0)}s",
                        color = Color(0xFF77717E),
                        fontSize = 12.sp,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailBox("TYPE", "TOTP", Modifier.weight(1f))
                    DetailBox("PERIOD", "${account.period} seconds", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailBox("ALGORITHM", account.algorithm, Modifier.weight(1f))
                    DetailBox("DIGITS", "${account.digits} digits", Modifier.weight(1f))
                }

                Button(
                    onClick = onCopy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF6330DB), Color(0xFF9147ED))),
                                RoundedCornerShape(13.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Copy code", fontWeight = FontWeight.ExtraBold)
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp)
                        .height(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, Color(0xFFE3DFE8)),
                ) {
                    Text("Delete account", color = Color(0xFFE24B55), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun DetailBox(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFFAF9FC), RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Text(label, color = Color(0xFF89838F), fontSize = 10.sp)
        Text(value, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
