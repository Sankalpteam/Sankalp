package com.indoone.settings.applock.setapplock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.R

private val Purple = Color(0xFF6330DB)
private val PurpleLight = Color(0xFF9147ED)
private val TextDark = Color(0xFF231D2B)
private val Muted = Color(0xFF76717D)
private val KeyBorder = Color(0xFFE5DFEC)
private val KeyBackground = Color(0xFFFAF9FC)

@Composable
fun SetAppLockScreen(
    pin: String,
    error: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
) {
    PinPadScaffold(
        title = "Create App PIN",
        description = "Create a 4–12 digit PIN to protect Sankalp.",
        pin = pin,
        error = error,
        actionLabel = "Create App PIN",
        actionEnabled = pin.length in 4..12,
        onDigit = onDigit,
        onBackspace = onBackspace,
        onClear = onClear,
        onAction = onCreate,
        onCancel = onCancel,
    )
}

@Composable
internal fun PinPadScaffold(
    title: String,
    description: String,
    pin: String,
    error: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onAction: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 430.dp)
            .heightIn(max = 680.dp)
            .padding(14.dp),
        shape = RoundedCornerShape(25.dp),
        color = Color.White,
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 430.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    // The mark, not a letter on a purple tile: that tile was the previous
                    // product's logo, and this screen was the last place still drawing it.
                    Image(
                        painter = painterResource(R.drawable.sankalp_logo),
                        contentDescription = "Sankalp logo",
                        modifier = Modifier.size(34.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text("Sankalp", color = Color(0xFF5E2DD2), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Text(title, fontSize = 27.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text(
                    description,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Muted,
                )

                Row(
                    modifier = Modifier.padding(top = 15.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    repeat(12) { index ->
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(
                                    color = if (index < pin.length) Purple else Color.Transparent,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }

                Text(
                    error,
                    modifier = Modifier.height(18.dp),
                    color = Color(0xFFCF2F2F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )

                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(
                                Brush.horizontalGradient(listOf(Purple, PurpleLight)),
                                RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) { Text(actionLabel, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 330.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫')
                keys.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            OutlinedButton(
                                onClick = when (key) {
                                    'C' -> onClear
                                    '⌫' -> onBackspace
                                    else -> ({ onDigit(key) })
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(3.dp)
                                    .height(50.dp),
                                shape = RoundedCornerShape(15.dp),
                                border = BorderStroke(1.dp, KeyBorder),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = KeyBackground,
                                    contentColor = if (key == 'C' || key == '⌫') Color(0xFF726A7B) else TextDark,
                                ),
                            ) {
                                Text(
                                    key.toString(),
                                    fontSize = if (key == 'C' || key == '⌫') 11.sp else 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(5.dp))
                if (onCancel != null) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel", color = Color(0xFF655B70), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}