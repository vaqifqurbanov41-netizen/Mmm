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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.domain.WeatherHazardAlert
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.notifications.EquipmentReminderManager
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.DangerBorder
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalAqroColors
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EquipmentAlert
import com.example.ui.viewmodel.EquipmentStatus
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
    weatherRegionName: String = "",
    alertCount: Int = if (hasAlertDot) 1 else 0,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val regionLabel = weatherRegionName.trim()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).padding(end = 6.dp),
            ) {
                AqroLauncherIconEmblem(size = 40.dp)
                Column {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = strings.brandTagline,
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onWeatherClick)
                        .testTag("header_weather_pill"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = strings.tileWeatherTitle,
                            tint = Color(0xFFF5A623),
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(
                                text = weatherTempText ?: "--°C",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            if (regionLabel.isNotBlank()) {
                                Text(
                                    text = regionLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = extra.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.size(40.dp),
                ) {
                    IconButton(
                        onClick = onThemeToggle,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("theme_toggle_button"),
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDark) strings.themeToggleLight else strings.themeToggleDark,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                val hasAnyNotif = hasAlertDot || alertCount > 0
                Box {
                    Surface(
                        shape = CircleShape,
                        color = if (hasAnyNotif) extra.dangerContainer else MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(if (hasAnyNotif) 1.5.dp else 1.dp, if (hasAnyNotif) DangerBorder else extra.line),
                        modifier = Modifier.size(40.dp),
                    ) {
                        IconButton(
                            onClick = onNotificationsClick,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .testTag("notifications_button"),
                        ) {
                            Icon(
                                imageVector = if (hasAnyNotif) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                contentDescription = strings.notificationsTitle,
                                tint = if (hasAnyNotif) DangerRed else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                    if (hasAnyNotif) {
                        val badgeLabel = when {
                            alertCount > 9 -> "9+"
                            alertCount > 0 -> alertCount.toString()
                            else -> ""
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 1.dp, y = (-1).dp)
                                .heightIn(min = if (badgeLabel.isEmpty()) 10.dp else 16.dp)
                                .clip(CircleShape)
                                .background(DangerRed)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .padding(horizontal = if (badgeLabel.isEmpty()) 5.dp else 4.dp)
                                .testTag("notifications_active_indicator"),
                        ) {
                            if (badgeLabel.isNotEmpty()) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
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

            GreenCameraScannerNavButton(
                selected = currentRoute == NavRoutes.SCANNER,
                contentDesc = strings.navScanner,
                tag = "nav_scanner",
                onClick = { onNavigate(NavRoutes.SCANNER) },
                modifier = Modifier.weight(1f),
            )

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
fun GreenCameraScannerNavButton(
    selected: Boolean,
    contentDesc: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(y = (-6).dp)
                .size(54.dp)
                .shadow(if (selected) 12.dp else 8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (selected) {
                            listOf(Color(0xFF0D4421), Color(0xFF218042))
                        } else {
                            listOf(Color(0xFF114C27), Color(0xFF288E4D))
                        }
                    )
                )
                .border(
                    width = if (selected) 3.dp else 2.5.dp,
                    color = if (selected) Color(0xFFA7F3D0) else MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = contentDesc,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
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
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 20.dp else 0.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
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

data class CustomizableModuleEntry(
    val route: String,
    val title: String,
    val subtitle: String,
)

@Composable
fun rememberCustomizableModules(isLocalOnly: Boolean): List<CustomizableModuleEntry> {
    val strings = LocalStrings.current
    return remember(strings, isLocalOnly) {
        buildList {
            add(CustomizableModuleEntry(NavRoutes.NPK, strings.tileNpkTitle, strings.tileNpkSub))
            add(CustomizableModuleEntry(NavRoutes.WEATHER, strings.tileWeatherTitle, strings.tileWeatherSub))
            add(CustomizableModuleEntry(NavRoutes.PESTICIDE, strings.tilePestTitle, strings.tilePestSub))
            add(CustomizableModuleEntry(NavRoutes.FIELDS, strings.tileFieldsTitle, strings.tileFieldsSub))
            add(CustomizableModuleEntry(NavRoutes.AGRO_CALENDAR, strings.tileCalendarTitle, strings.tileCalendarSub))
            add(CustomizableModuleEntry(NavRoutes.INSURANCE, strings.tileInsuranceTitle, strings.tileInsuranceSub))
            add(CustomizableModuleEntry(NavRoutes.STATIONS, strings.tileStationsTitle, strings.tileStationsSub))
            add(CustomizableModuleEntry(NavRoutes.SUBSIDY, strings.tileSubsidyTitle, strings.tileSubsidySub))
            add(CustomizableModuleEntry(NavRoutes.EQUIPMENT, strings.tileEquipmentTitle, strings.tileEquipmentSub))
            add(CustomizableModuleEntry(NavRoutes.FINANCE, strings.tileFinanceTitle, strings.tileFinanceSub))
            if (!isLocalOnly) {
                add(CustomizableModuleEntry(NavRoutes.HARVEST, strings.tileHarvestTitle, strings.tileHarvestSub))
            }
            add(CustomizableModuleEntry(NavRoutes.GALLERY, strings.tileGalleryTitle, strings.tileGallerySub))
            add(CustomizableModuleEntry(NavRoutes.SEEDS, strings.tileSeedsTitle, strings.tileSeedsSub))
            add(CustomizableModuleEntry(NavRoutes.LINKS, strings.tileLinksTitle, strings.tileLinksSub))
        }
    }
}

@Composable
fun ModuleVisibilityDialog(
    visible: Boolean,
    isLocalOnly: Boolean,
    hiddenModules: Set<String>,
    onToggleModule: (String, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val modules = rememberCustomizableModules(isLocalOnly)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.modulesDialogTitle, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = strings.modulesDialogSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .testTag("modules_visibility_dialog_list"),
            ) {
                items(modules, key = { it.route }) { mod ->
                    val isVisible = mod.route !in hiddenModules
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isVisible) extra.pale else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, extra.line),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleModule(mod.route, !isVisible) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = mod.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = mod.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(
                                checked = isVisible,
                                onCheckedChange = { checked -> onToggleModule(mod.route, checked) },
                                modifier = Modifier.testTag("module_switch_${mod.route}"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_modules_dialog_btn"),
            ) {
                Text(strings.save, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onShowAll,
                modifier = Modifier.testTag("reset_modules_btn"),
            ) {
                Text(strings.modulesShowAllBtn)
            }
        },
    )
}

@Composable
fun NotificationsDialog(
    visible: Boolean,
    weatherHazards: List<WeatherHazardAlert>,
    equipmentAlerts: List<EquipmentAlert>,
    onOpenWeather: () -> Unit,
    onOpenEquipment: () -> Unit,
    onDismiss: () -> Unit,
    onClearAll: (() -> Unit)? = null,
    onDismissWeather: ((WeatherHazardAlert) -> Unit)? = null,
    onDismissEquipment: ((EquipmentAlert) -> Unit)? = null,
) {
    if (!visible) return
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (weatherHazards.isNotEmpty() || equipmentAlerts.isNotEmpty()) {
                        Icons.Filled.NotificationsActive
                    } else {
                        Icons.Filled.Notifications
                    },
                    contentDescription = strings.notificationsTitle,
                    tint = if (weatherHazards.isNotEmpty() || equipmentAlerts.isNotEmpty()) DangerRed else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = strings.notifDialogTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .testTag("notifications_dialog_list"),
            ) {
                if (weatherHazards.isEmpty() && equipmentAlerts.isEmpty()) {
                    item {
                        EmptyStateMessage(
                            icon = Icons.Filled.Notifications,
                            message = strings.notifEmptyMsg,
                        )
                    }
                }

                if (weatherHazards.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.notifSectionWeatherTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = DangerRed,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(weatherHazards, key = { "dlg_wh_${it.id}" }) { alert ->
                        val (title, advice) = EquipmentReminderManager.weatherHazardSummary(alert, strings)
                        val srcLabel = EquipmentReminderManager.sourceKindLabel(alert.sourceKind, strings)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = extra.dangerContainer,
                            border = BorderStroke(1.dp, DangerBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notif_weather_item_${alert.id}"),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = DangerRed,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Text(
                                            text = "${alert.date} • ${alert.metricText}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    if (onDismissWeather != null) {
                                        IconButton(
                                            onClick = { onDismissWeather(alert) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("dismiss_weather_${alert.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = strings.cancel,
                                                tint = extra.muted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${alert.locationLabel} ($srcLabel)${if (alert.cropNames.isNotBlank()) " • ${alert.cropNames}" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = advice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extra.onDangerContainer,
                                )
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenWeather()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("notif_open_weather_btn"),
                        ) {
                            Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(strings.notifViewWeatherBtn)
                        }
                    }
                }

                if (weatherHazards.isNotEmpty() && equipmentAlerts.isNotEmpty()) {
                    item { HorizontalDivider(color = extra.line) }
                }

                if (equipmentAlerts.isNotEmpty()) {
                    item {
                        val hasOverdue = equipmentAlerts.any { it.status == EquipmentStatus.OVERDUE }
                        val headerAccent = if (hasOverdue) DangerRed else WarningAmber
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (hasOverdue) DangerBorder.copy(alpha = 0.6f) else extra.line),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(headerAccent.copy(alpha = 0.14f)),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Build,
                                            contentDescription = null,
                                            tint = headerAccent,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                    Text(
                                        text = strings.notifSectionEquipmentTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable {
                                                onDismiss()
                                                onOpenEquipment()
                                            }
                                            .testTag("notif_open_equipment_btn"),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = strings.open,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = strings.notifViewEquipmentBtn,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    equipmentAlerts.forEach { eqAlert ->
                                        val isOverdue = eqAlert.status == EquipmentStatus.OVERDUE
                                        val statusColor = if (isOverdue) DangerRed else WarningAmber
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    onDismiss()
                                                    onOpenEquipment()
                                                }
                                                .testTag("notif_equipment_item_${eqAlert.itemName}"),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(statusColor),
                                                )
                                                Text(
                                                    text = eqAlert.itemName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = statusColor.copy(alpha = 0.13f),
                                                ) {
                                                    Text(
                                                        text = if (isOverdue) strings.eqStatusOverdue else strings.eqStatusSoon,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusColor,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    )
                                                }
                                                if (onDismissEquipment != null) {
                                                    IconButton(
                                                        onClick = { onDismissEquipment(eqAlert) },
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .testTag("dismiss_equipment_${eqAlert.itemName}"),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Close,
                                                            contentDescription = strings.cancel,
                                                            tint = extra.muted,
                                                            modifier = Modifier.size(14.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = if (onClearAll != null && (weatherHazards.isNotEmpty() || equipmentAlerts.isNotEmpty())) {
            {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = DangerRed),
                    modifier = Modifier.testTag("clear_all_notifications_btn"),
                ) {
                    Text(strings.notifClearAllBtn, fontWeight = FontWeight.Bold)
                }
            }
        } else null,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_notifications_dialog_btn"),
            ) {
                Text(strings.coordDoneBtn, fontWeight = FontWeight.Bold)
            }
        },
        modifier = Modifier.testTag("notifications_dialog"),
    )
}

fun formatNumber(value: Double, decimals: Int = 2): String =
    String.format(Locale.US, "%.${decimals}f", value)
