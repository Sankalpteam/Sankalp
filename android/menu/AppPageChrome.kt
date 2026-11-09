package com.indoone.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.indoone.R
import com.indoone.vibe.VibeButton

private fun iconBuilder(name: String, content: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            pathBuilder = content,
        )
    }.build()

private fun filledIconBuilder(name: String, content: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathBuilder = content,
        )
    }.build()

private fun mixedIconBuilder(name: String, content: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            pathBuilder = content,
        )
    }.build()

private val AccountsNavIcon = filledIconBuilder("HomeNav") {
    moveTo(3f, 10.5f); lineTo(12f, 3.5f); lineTo(21f, 10.5f)
    lineTo(19f, 10.5f); lineTo(19f, 20f); lineTo(14f, 20f)
    lineTo(14f, 14.5f); lineTo(10f, 14.5f); lineTo(10f, 20f)
    lineTo(5f, 20f); lineTo(5f, 10.5f); close()
}

private val LobbyNavIcon = filledIconBuilder("LobbyNav") {
    moveTo(12f, 2.8f); lineTo(14.5f, 9.5f); lineTo(21.2f, 12f)
    lineTo(14.5f, 14.5f); lineTo(12f, 21.2f); lineTo(9.5f, 14.5f)
    lineTo(2.8f, 12f); lineTo(9.5f, 9.5f); close()
}

private val ConnectNavIcon = mixedIconBuilder("ConnectNav") {
    moveTo(7f, 7f); lineTo(17f, 17f)
    moveTo(17f, 7f); lineTo(7f, 17f)
    moveTo(7f, 7f); curveTo(8.657f, 7f, 10f, 8.343f, 10f, 10f)
    curveTo(10f, 11.657f, 8.657f, 13f, 7f, 13f)
    curveTo(5.343f, 13f, 4f, 11.657f, 4f, 10f)
    curveTo(4f, 8.343f, 5.343f, 7f, 7f, 7f)
    moveTo(17f, 11f); curveTo(18.657f, 11f, 20f, 12.343f, 20f, 14f)
    curveTo(20f, 15.657f, 18.657f, 17f, 17f, 17f)
    curveTo(15.343f, 17f, 14f, 15.657f, 14f, 14f)
    curveTo(14f, 12.343f, 15.343f, 11f, 17f, 11f)
}

private val SettingsNavIcon = mixedIconBuilder("SettingsNav") {
    moveTo(5f, 6.5f); lineTo(19f, 6.5f)
    moveTo(5f, 12f); lineTo(19f, 12f)
    moveTo(5f, 17.5f); lineTo(19f, 17.5f)
    moveTo(9f, 6.5f); curveTo(9f, 7.605f, 8.105f, 8.5f, 7f, 8.5f)
    curveTo(5.895f, 8.5f, 5f, 7.605f, 5f, 6.5f)
    curveTo(5f, 5.395f, 5.895f, 4.5f, 7f, 4.5f)
    curveTo(8.105f, 4.5f, 9f, 5.395f, 9f, 6.5f)
    moveTo(16f, 12f); curveTo(16f, 13.105f, 15.105f, 14f, 14f, 14f)
    curveTo(12.895f, 14f, 12f, 13.105f, 12f, 12f)
    curveTo(12f, 10.895f, 12.895f, 10f, 14f, 10f)
    curveTo(15.105f, 10f, 16f, 10.895f, 16f, 12f)
    moveTo(19f, 17.5f); curveTo(19f, 18.605f, 18.105f, 19.5f, 17f, 19.5f)
    curveTo(15.895f, 19.5f, 15f, 18.605f, 15f, 17.5f)
    curveTo(15f, 16.395f, 15.895f, 15.5f, 17f, 15.5f)
    curveTo(18.105f, 15.5f, 19f, 16.395f, 19f, 17.5f)
}

val AppSearchIcon = iconBuilder("Search") {
    moveTo(11f, 17.5f); curveTo(7.41f, 17.5f, 4.5f, 14.59f, 4.5f, 11f); curveTo(4.5f, 7.41f, 7.41f, 4.5f, 11f, 4.5f); curveTo(14.59f, 4.5f, 17.5f, 7.41f, 17.5f, 11f); curveTo(17.5f, 14.59f, 14.59f, 17.5f, 11f, 17.5f)
    moveTo(16f, 16f); lineTo(20f, 20f)
}
private val MenuIcon = iconBuilder("Menu") {
    moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 17f); lineTo(20f, 17f)
}

@Composable
internal fun IndooneTopBarLogo(modifier: Modifier = Modifier) {
    // The artwork itself, not a shape drawn in Compose. It was a purple gradient diamond
    // with a spark in it - the mark of the product this one replaced - and three screens
    // each redrew it independently, so it could drift in three directions at once.
    Image(
        painter = painterResource(R.drawable.sankalp_logo),
        contentDescription = "Sankalp logo",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun AppTopBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    trailingIcon: String? = null,
    onTrailingClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: (() -> Unit)? = null,
) {

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            TextButton(
                onClick = onMenuClick,
                modifier = Modifier.align(Alignment.CenterStart),
                contentPadding = PaddingValues(8.dp),
            ) {
                Icon(
                    MenuIcon,
                    contentDescription = "Open menu",
                    modifier = Modifier.size(21.dp),
                    tint = Color(0xFF242129),
                )
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                IndooneTopBarLogo(
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    "Indoone",
                    color = Color(0xFF5E2DD2),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                )
            }

            if (onNotificationsClick != null) {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = if (hasUnreadNotifications) {
                            Icons.Filled.Notifications
                        } else {
                            Icons.Outlined.NotificationsNone
                        },
                        contentDescription = "Notifications",
                        modifier = Modifier.size(22.dp),
                        tint = Color(0xFF242129),
                    )
                }
            } else {
                TextButton(
                    onClick = if (trailingIcon != null) onTrailingClick else onSearchClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    if (trailingIcon == null) {
                        Icon(
                            AppSearchIcon,
                            contentDescription = "Search accounts",
                            modifier = Modifier.size(21.dp),
                            tint = Color(0xFF242129),
                        )
                    } else {
                        Text(trailingIcon, color = Color(0xFF242129), fontSize = 24.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFFF0EEF5))
    }
}

@Composable
fun AppBottomNav(
    activeTab: AppTab,
    onAccountsClick: () -> Unit,
    onLobbyClick: () -> Unit,
    onVibeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(30.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Home, Lobby and Settings keep the navigation bar's own design: the
            // rounded, shadowed bar and the neumorphic items belong to these three.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .shadow(elevation = 13.dp, shape = barShape, clip = false)
                    .clip(barShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFAF8FF),
                                Color(0xFFF5F0FA),
                                Color(0xFFEEE7F7),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.98f)),
                        shape = barShape,
                    )
                    .padding(horizontal = 5.dp, vertical = 5.dp)
                    .height(62.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeumorphicNavItem(
                    icon = Icons.Filled.Home,
                    label = "Home",
                    active = activeTab == AppTab.HOME,
                    onClick = onAccountsClick,
                )
                NeumorphicNavItem(
                    icon = Icons.Outlined.Groups,
                    label = "Lobby",
                    active = activeTab == AppTab.LOBBY,
                    onClick = onLobbyClick,
                )
                NeumorphicNavItem(
                    icon = Icons.Outlined.Settings,
                    label = "Settings",
                    active = activeTab == AppTab.SETTINGS,
                    onClick = onSettingsClick,
                )
            }

            // Vibe stands apart from the navigation: it keeps the exact button it
            // had on Home, with none of the navigation bar's background or styling.
            VibeButton(
                modifier = Modifier.padding(start = 8.dp),
                onClick = onVibeClick,
            )
        }
    }
}

enum class AppTab { HOME, LOBBY, SETTINGS }

@Composable
private fun RowScope.NeumorphicNavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val itemShape = RoundedCornerShape(15.dp)
    val activeBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF0E6FF),
            Color(0xFFE0CEFF),
            Color(0xFFE9DAFA),
        ),
    )
    val activeColor = Color(0xFF6D35D0)
    val normalColor = Color(0xFF344157)
    val contentColor = if (active) activeColor else normalColor

    Column(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .padding(horizontal = 2.dp)
            .then(
                if (active) {
                    Modifier
                        .shadow(elevation = 7.dp, shape = itemShape, clip = false)
                        .clip(itemShape)
                        .background(activeBrush)
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.98f)),
                            itemShape,
                        )
                } else {
                    Modifier
                        .clip(itemShape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(19.dp),
            tint = contentColor,
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            lineHeight = 11.sp,
            maxLines = 1,
        )
    }
}

private val AppChevronTint = Color(0xFF7A6F8C)
private val AppBackButtonTint = Color(0xFF242129)
private val AppBackButtonBackground = Color(0xFFF5F2F8)

/**
 * The one back control. Some screens spelled out "‹  Back" as text, at one size,
 * and others used a glyph at another; this is the arrow mark on its own, and it is
 * the same everywhere.
 */
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(35.dp)
            .background(AppBackButtonBackground, RoundedCornerShape(11.dp)),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = AppBackButtonTint,
        )
    }
}

/**
 * The one arrow used by every row that opens something. Some rows drew a "›"
 * text glyph and others used an icon at a different size and tint, which read as
 * missing or wrong. Rows call this instead.
 */
@Composable
fun AppChevron(
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = contentDescription,
        modifier = modifier.size(22.dp),
        tint = AppChevronTint,
    )
}
