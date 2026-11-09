package com.indoone.settings.autolock

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.menu.AppChevron

@Composable
fun AutoLockScreen(
    currentMinutes: Int,
    appLockEnabled: Boolean,
    biometricEnabled: Boolean,
    onSelectMinutes: (Int) -> Unit,
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
                .padding(14.dp)
                .clickable(onClick = {}),
            color = Color.White,
            shadowElevation = 14.dp,
            shape = RoundedCornerShape(25.dp),
        ) {
            Column(Modifier.padding(horizontal = 23.dp, vertical = 21.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Auto-Lock",
                            fontSize = 21.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C2733),
                        )
                        Text(
                            "Automatically lock Sankalp after a period of inactivity.",
                            modifier = Modifier.padding(top = 5.dp),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF756F7F),
                        )
                    }
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

                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15).forEach { minutes ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectMinutes(minutes) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentMinutes == minutes) Color(0xFFFAF7FF) else Color.White,
                            border = BorderStroke(
                                1.dp,
                                if (currentMinutes == minutes) Color(0xFFCBB8EC) else Color(0xFFE6E0EE),
                            ),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "After $minutes minute${if (minutes == 1) "" else "s"}",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (currentMinutes == minutes) Color(0xFF5C2AC7) else Color(0xFF2C2733),
                                )
                                if (currentMinutes == minutes) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color(0xFF5C2AC7),
                                    )
                                } else {
                                    AppChevron()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
