package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.DangerBorder
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalAqroColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val KTN_TELEGRAM_BOT_URL = "https://t.me/ktn_asi_bot"

fun openExternalUrl(context: Context, rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return
    val formatted = if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("tg:", ignoreCase = true) ||
        trimmed.startsWith("tel:", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(formatted)))
    }
}

fun todayIsoDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
fun AqroTopHeader(
    farmerFirstName: String,
    language: AppLanguage,
    weatherTempText: String?,
    hasAlertDot: Boolean,
    isDark: Boolean,
    onWeatherClick: () -> Unit,
    onThemeToggle: () -> Unit,
    onNotificationsClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val dateText = remember(language) {
        SimpleDateFormat("d MMMM", language.locale).format(Date())
    }
    val greeting = if (farmerFirstName.isNotBlank()) {
        "${strings.greetingPrefix}, $farmerFirstName!"
    } else {
        "${strings.greetingPrefix}, ${strings.defaultFarmerName}!"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateText.uppercase(language.locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted,
                )
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = extra.pale,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onWeatherClick)
                        .testTag("header_weather_pill"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = strings.tileWeatherTitle,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = weatherTempText ?: "--°C",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                IconButton(
                    onClick = onThemeToggle,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(extra.pale)
                        .testTag("theme_toggle_button"),
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (isDark) strings.themeToggleLight else strings.themeToggleDark,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Box {
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(extra.pale)
                            .testTag("notifications_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = strings.notificationsTitle,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (hasAlertDot) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 6.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(DangerRed)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AqroBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, extra.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            BottomNavItem(
                icon = Icons.Filled.Home,
                label = strings.navHome,
                selected = currentRoute == NavRoutes.HOME,
                tag = "nav_home",
                onClick = { onNavigate(NavRoutes.HOME) },
                modifier = Modifier.weight(1f),
            )
            BottomNavItem(
                icon = Icons.Filled.Build,
                label = strings.navTools,
                selected = currentRoute == NavRoutes.NPK ||
                    currentRoute == NavRoutes.PESTICIDE ||
                    currentRoute == NavRoutes.SUBSIDY ||
                    currentRoute == NavRoutes.EQUIPMENT ||
                    currentRoute == NavRoutes.FINANCE ||
                    currentRoute == NavRoutes.HARVEST ||
                    currentRoute == NavRoutes.GALLERY ||
                    currentRoute == NavRoutes.SEEDS ||
                    currentRoute == NavRoutes.AGRO_CALENDAR ||
                    currentRoute == NavRoutes.INSURANCE ||
                    currentRoute == NavRoutes.STATIONS ||
                    currentRoute == NavRoutes.ADMIN ||
                    currentRoute == NavRoutes.LINKS ||
                    currentRoute == NavRoutes.WEATHER ||
                    currentRoute == NavRoutes.CHAT,
                tag = "nav_tools",
                onClick = { onNavigate(NavRoutes.NPK) },
                modifier = Modifier.weight(1f),
            )

            // Elevated Center Plant Scanner Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clickable { onNavigate(NavRoutes.SCANNER) }
                    .testTag("nav_scanner"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(y = (-8).dp)
                        .size(48.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(extra.heroStart, extra.heroEnd)
                            )
                        )
                        .border(2.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = strings.navScanner,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = strings.navScanner,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (currentRoute == NavRoutes.SCANNER) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        extra.muted
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            BottomNavItem(
                icon = Icons.Filled.Map,
                label = strings.navFields,
                selected = currentRoute == NavRoutes.FIELDS,
                tag = "nav_fields",
                onClick = { onNavigate(NavRoutes.FIELDS) },
                modifier = Modifier.weight(1f),
            )
            BottomNavItem(
                icon = Icons.Filled.Person,
                label = strings.navProfile,
                selected = currentRoute == NavRoutes.PROFILE,
                tag = "nav_profile",
                onClick = { onNavigate(NavRoutes.PROFILE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    val tint = if (selected) MaterialTheme.colorScheme.primary else extra.muted

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extra.line),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack)
                .testTag("back_button"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.back,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = strings.back,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun AqroCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = LocalAqroColors.current.line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun TabPillsRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = extra.pale,
        border = BorderStroke(1.dp, extra.line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                val active = index == selectedIndex
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (active) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable { onSelect(index) }
                        .testTag("tab_pill_$index"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.primary else extra.muted,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DangerBannerBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = extra.dangerContainer,
        border = BorderStroke(1.5.dp, DangerBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = extra.onDangerContainer,
            )
        }
    }
}

@Composable
fun EmptyStateMessage(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = extra.muted,
        )
    }
}

@Composable
fun ConfirmDeleteDialog(
    visible: Boolean,
    itemLabel: String = "",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.confirmDeleteTitle, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (itemLabel.isNotBlank()) "${strings.confirmDeleteMessage}\n($itemLabel)"
                else strings.confirmDeleteMessage
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = DangerRed),
            ) {
                Text(strings.yesDelete, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}

fun formatNumber(value: Double, decimals: Int = 2): String =
    String.format(Locale.US, "%.${decimals}f", value)
