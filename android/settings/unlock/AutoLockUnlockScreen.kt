package com.indoone.settings.unlock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.R

// The app's own palette, so the lock screen looks like the rest of Sankalp.
private val LockPurple = Color(0xFF6330DB)
private val LockPurpleLight = Color(0xFF9147ED)
private val LockPurpleDark = Color(0xFF5E2DD2)
private val LockText = Color(0xFF17151D)
private val LockBorder = Color(0xFFE3DFE8)
private val LockBackground = Color(0xFFF8F4FF)
private val LockDotEmpty = Color(0xFFE1D8F2)
private val LockError = Color(0xFFB3261E)

@Composable
fun AutoLockUnlockScreen(
    biometricEnabled: Boolean,
    onUnlockWithPin: (String) -> Boolean,
    onUnlockWithBiometric: () -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var biometricStarted by remember { mutableStateOf(false) }

    fun append(digit: String) {
        if (pin.length < 12) {
            pin += digit
            error = ""
        }
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !biometricStarted) {
            biometricStarted = true
            onUnlockWithBiometric()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = LockBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.sankalp_logo),
                contentDescription = "Sankalp logo",
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Sankalp",
                color = LockPurpleDark,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Unlock Sankalp",
                color = LockText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(12) { index ->
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                color = if (index < pin.length) LockPurple else LockDotEmpty,
                                shape = CircleShape,
                            ),
                    )
                }
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    modifier = Modifier.padding(top = 10.dp),
                    color = LockError,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Clear", "0", "⌫")
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { key ->
                        OutlinedButton(
                            onClick = {
                                when (key) {
                                    "Clear" -> {
                                        pin = ""
                                        error = ""
                                    }

                                    "⌫" -> {
                                        pin = pin.dropLast(1)
                                        error = ""
                                    }

                                    else -> append(key)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, LockBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LockText),
                        ) {
                            Text(key, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    if (pin.length !in 4..12) {
                        error = "Enter a 4–12 digit PIN."
                    } else if (onUnlockWithPin(pin)) {
                        error = ""
                    } else {
                        error = "Incorrect PIN"
                        pin = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(LockPurple, LockPurpleLight))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Unlock with App PIN", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            if (biometricEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        biometricStarted = true
                        onUnlockWithBiometric()
                    },
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LockBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LockPurpleDark),
                ) {
                    Icon(
                        Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Use fingerprint", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}
