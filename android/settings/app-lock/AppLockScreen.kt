package com.indoone.settings.applock

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppLockScreen(
    hasPin: Boolean,
    onSet: () -> Unit,
    onChange: () -> Unit,
    onDisable: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x5519141F))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                .clickable(onClick = {}),
            shape = RoundedCornerShape(25.dp),
            color = Color.White,
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(23.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "App Lock",
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C2733),
                    )
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(35.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFFF5F2F8)),
                    ) {
                        Text("×", fontSize = 21.sp, color = Color(0xFF2C2733))
                    }
                }

                Text(
                    text = if (hasPin) "Manage your Sankalp app PIN." else "Protect Sankalp with a 4–12 digit App PIN.",
                    modifier = Modifier.padding(top = 7.dp),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Color(0xFF756F7F),
                )

                Spacer(Modifier.height(16.dp))

                if (hasPin) {
                    Button(
                        onClick = onChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(13.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF6330DB), Color(0xFF9147ED)))),
                            contentAlignment = Alignment.Center,
                        ) { Text("Change App PIN", fontWeight = FontWeight.Bold) }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onDisable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(13.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4DDEA)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF655B70)),
                    ) { Text("Disable App Lock", fontWeight = FontWeight.Bold) }
                } else {
                    Button(
                        onClick = onSet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF6330DB), Color(0xFF9147ED))),
                                RoundedCornerShape(13.dp),
                            ),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Set App Lock", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
