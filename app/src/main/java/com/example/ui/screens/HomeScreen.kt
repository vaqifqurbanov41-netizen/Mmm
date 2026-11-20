package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AdminContentItem
import com.example.data.model.FarmField
import com.example.data.model.isAdminEmail
import com.example.domain.WeatherHazardAlert
import com.example.i18n.LocalStrings
import com.example.notifications.EquipmentReminderManager
import com.example.ui.components.AqroCard
import com.example.ui.components.BotanicalCornerBackdrop
import com.example.ui.components.KTN_TELEGRAM_BOT_URL
import com.example.ui.components.ModuleIconMedallion
import com.example.ui.components.ModuleVisibilityDialog
import com.example.ui.components.formatNumber
import com.example.ui.components.openExternalUrl
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.DangerBorder
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAqroColors
import com.example.ui.theme.PhosphorusAmber
import com.example.ui.theme.PotassiumPlum
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EquipmentAlert
import com.example.ui.viewmodel.WeatherUiState

private data class HomeMenuTile(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val tag: String,
    val alwaysVisible: Boolean = false,
)

@Composable
fun HomeScreen(
    fields: List<FarmField>,
    weatherState: WeatherUiState,
    equipmentAlerts: List<EquipmentAlert>,
    onNavigate: (String) -> Unit,
    userEmail: String? = null,
    adminEntries: List<AdminContentItem> = emptyList(),
    hiddenModules: Set<String> = emptySet(),
    onToggleModuleVisible: (String, Boolean) -> Unit = { _, _ -> },
    onResetModules: () -> Unit = {},
    farmerFirstName: String = "",
    weatherHazards: List<WeatherHazardAlert> = emptyList(),
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    var showCustomizeDialog by remember { mutableStateOf(false) }

    val totalHa = fields.sumOf { it.sizeHa }
    val showAdminTile = isAdminEmail(userEmail)
    val adminNotices = adminEntries.filter { it.category == "notice" }
    val displayName = farmerFirstName.ifBlank { strings.defaultFarmerName }

    val allTiles = buildList {
        add(HomeMenuTile(NavRoutes.CHAT, strings.aiBannerTitle, strings.aiBannerSub, Icons.Filled.SmartToy, EmeraldPrimary, "tile_ai_agronom", alwaysVisible = true))
        add(HomeMenuTile(NavRoutes.PESTICIDE, strings.tilePestTitle, strings.tilePestSub, Icons.Filled.Science, WarningAmber, "tile_pesticide"))
        add(HomeMenuTile(NavRoutes.NPK, strings.tileNpkTitle, strings.tileNpkSub, Icons.Filled.Grass, EmeraldPrimary, "tile_npk"))
        add(HomeMenuTile(NavRoutes.WEATHER, strings.tileWeatherTitle, strings.tileWeatherSub, Icons.Filled.WbSunny, Color(0xFF2F80ED), "tile_weather"))
        add(HomeMenuTile(NavRoutes.FIELDS, strings.tileFieldsTitle, strings.tileFieldsSub, Icons.Filled.Map, Color(0xFF1F8A70), "tile_fields"))
        add(HomeMenuTile(NavRoutes.HARVEST, strings.tileHarvestTitle, strings.tileHarvestSub, Icons.Filled.Spa, EmeraldBright, "tile_harvest"))
        add(HomeMenuTile(NavRoutes.SUBSIDY, strings.tileSubsidyTitle, strings.tileSubsidySub, Icons.Filled.RequestQuote, PhosphorusAmber, "tile_subsidy"))
        add(HomeMenuTile(NavRoutes.EQUIPMENT, strings.tileEquipmentTitle, strings.tileEquipmentSub, Icons.Filled.Agriculture, PotassiumPlum, "tile_equipment"))
        add(HomeMenuTile(NavRoutes.FINANCE, strings.tileFinanceTitle, strings.tileFinanceSub, Icons.Filled.AccountBalanceWallet, Color(0xFF0F766E), "tile_finance"))
        add(HomeMenuTile(NavRoutes.SEEDS, strings.tileSeedsTitle, strings.tileSeedsSub, Icons.Filled.LocalFlorist, Color(0xFF15803D), "tile_seeds"))
        add(HomeMenuTile(NavRoutes.AGRO_CALENDAR, strings.tileCalendarTitle, strings.tileCalendarSub, Icons.Filled.CalendarMonth, Color(0xFF7E22CE), "tile_calendar"))
        add(HomeMenuTile(NavRoutes.INSURANCE, strings.tileInsuranceTitle, strings.tileInsuranceSub, Icons.Filled.VerifiedUser, Color(0xFF0369A1), "tile_insurance"))
        add(HomeMenuTile(NavRoutes.STATIONS, strings.tileStationsTitle, strings.tileStationsSub, Icons.Filled.Storefront, Color(0xFF0D9488), "tile_stations"))
        add(HomeMenuTile(NavRoutes.GALLERY, strings.tileGalleryTitle, strings.tileGallerySub, Icons.Filled.PhotoCamera, Color(0xFF0284C7), "tile_gallery"))
        add(HomeMenuTile(NavRoutes.LINKS, strings.tileLinksTitle, strings.tileLinksSub, Icons.Filled.Link, Color(0xFF4F46E5), "tile_links"))
        if (showAdminTile) {
            add(HomeMenuTile(NavRoutes.ADMIN, strings.tileAdminTitle, strings.tileAdminSub, Icons.Filled.AdminPanelSettings, Color(0xFFB45309), "tile_admin", alwaysVisible = true))
        }
    }
    val visibleCustomizableCount = allTiles.count { !it.alwaysVisible && it.route !in hiddenModules }
    val tiles = allTiles.filter { it.alwaysVisible || it.route !in hiddenModules }

    ModuleVisibilityDialog(
        visible = showCustomizeDialog,
        isLocalOnly = false,
        hiddenModules = hiddenModules,
        onToggleModule = onToggleModuleVisible,
        onShowAll = onResetModules,
        onDismiss = { showCustomizeDialog = false },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        BotanicalCornerBackdrop(modifier = Modifier.fillMaxSize())

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().testTag("home_screen_list"),
        ) {
            // Illustrated Hero Greeting & Field Metrics Card
            item {
                HomeHeroBannerCard(
                    greeting = "${strings.greetingPrefix}, $displayName!",
                    subtitle = strings.heroSubtitle,
                    fieldsLabel = strings.myFieldsStat,
                    fieldsCount = fields.size.toString(),
                    areaLabel = strings.totalAreaStat,
                    areaValue = "${formatNumber(totalHa, 2)} ha",
                    onFieldsClick = { onNavigate(NavRoutes.FIELDS) },
                )
            }

            // Weather & Fertilization Hazard Alert Card (only shown when frost, heat, high wind, or heavy rain is detected)
            if (weatherState.hasAnyHazard || weatherHazards.isNotEmpty()) {
                item {
                    val alertTitle = strings.alertWeatherTitle
                    val hazardReasons = buildList {
                        if (weatherState.hasFrostRisk) add(strings.frostWarning)
                        if (weatherState.hasHeatRisk) add(strings.heatWarning)
                        if (weatherState.hasHighWind) add(strings.windWarning)
                        if (weatherState.hasHeavyRain) add(strings.rainWarning)
                    }
                    val locationSummaries = weatherHazards.take(2).map { alert ->
                        val (title, _) = EquipmentReminderManager.weatherHazardSummary(alert, strings)
                        "${alert.locationLabel}: $title (${alert.date}, ${alert.metricText})"
                    }
                    val alertSubtitle = (locationSummaries + hazardReasons)
                        .distinct()
                        .joinToString(" • ")

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = extra.dangerContainer),
                        border = BorderStroke(1.dp, DangerBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onNavigate(NavRoutes.WEATHER) }
                            .testTag("home_alert_card"),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed.copy(alpha = 0.14f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = alertTitle,
                                    tint = DangerRed,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = alertTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = alertSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extra.onDangerContainer,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = strings.open,
                                tint = DangerRed,
                            )
                        }
                    }
                }
            }

            // Admin Published Notices (visible to all signed-in users)
            if (adminNotices.isNotEmpty()) {
                items(adminNotices, key = { "home_notice_${it.id}" }) { notice ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = extra.pale),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { mod ->
                                if (notice.contactOrUrl.isNotBlank()) {
                                    mod.clip(RoundedCornerShape(18.dp)).clickable { openExternalUrl(context, notice.contactOrUrl) }
                                } else {
                                    mod
                                }
                            }
                            .testTag("home_admin_notice_${notice.id}"),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(14.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AdminPanelSettings,
                                    contentDescription = strings.adminPublishedBannerTitle,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = strings.adminPublishedBannerTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (notice.region.isNotBlank()) {
                                    Text(
                                        text = "• ${notice.region}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.muted,
                                    )
                                }
                            }
                            Text(
                                text = notice.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = notice.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // Modules Header + Customize Visibility Button
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = strings.modulesSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(
                        onClick = { showCustomizeDialog = true },
                        modifier = Modifier.testTag("customize_modules_btn"),
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(strings.modulesCustomizeBtn, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Adaptive 3-Column Visual Module Grid
            item {
                if (visibleCustomizableCount == 0) {
                    AqroCard {
                        Text(
                            text = strings.modulesAllHiddenMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = extra.muted,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onResetModules,
                            modifier = Modifier.fillMaxWidth().testTag("show_all_modules_inline_btn"),
                        ) {
                            Text(strings.modulesShowAllBtn)
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("home_tiles_grid")) {
                        val cols = if (maxWidth >= 600.dp) 4 else 3
                        val rows = tiles.chunked(cols)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows.forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                ) {
                                    rowItems.forEach { tile ->
                                        HomeMenuTileCard(
                                            tile = tile,
                                            onClick = { onNavigate(tile.route) },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        )
                                    }
                                    repeat(cols - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Illustrated AqroM Assistant Banner Card (Online)
            item {
                HomeAssistantBannerCard(
                    title = strings.aiBannerTitle,
                    badgeLabel = strings.assistantOnlineLabel,
                    subtitle = strings.aiBannerSub,
                    actionLabel = strings.askNowBtn,
                    tag = "home_ai_banner",
                    onClick = { onNavigate(NavRoutes.CHAT) },
                )
            }

            // KTN ASI Telegram Bot Card
            item {
                HomeTelegramBotCard(
                    title = strings.telegramBotTitle,
                    subtitle = strings.telegramBotSub,
                    actionLabel = strings.telegramBotBtn,
                    tag = "home_telegram_bot_card",
                    onClick = { openExternalUrl(context, KTN_TELEGRAM_BOT_URL) },
                )
            }

            // Bottom Twin Strip: Weather Alerts & Seasonal Agro Calendar
            item {
                HomeBottomQuickStrip(
                    weatherState = weatherState,
                    weatherHazards = weatherHazards,
                    onOpenWeather = { onNavigate(NavRoutes.WEATHER) },
                    onOpenCalendar = { onNavigate(NavRoutes.AGRO_CALENDAR) },
                )
            }
        }
    }
}

@Composable
fun HomeHeroBannerCard(
    greeting: String,
    subtitle: String,
    fieldsLabel: String,
    fieldsCount: String,
    areaLabel: String,
    areaValue: String,
    onFieldsClick: () -> Unit,
    showStats: Boolean = true,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E4A25)),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_home_hero_farm),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Deep forest emerald gradient overlay on left/bottom so text and glass stats always pop cleanly
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color(0xFF0A3D1E).copy(alpha = 0.94f),
                            0.48f to Color(0xFF104F29).copy(alpha = 0.78f),
                            0.78f to Color(0xFF135A2F).copy(alpha = 0.38f),
                            1.0f to Color(0xFF0E4523).copy(alpha = 0.25f),
                        )
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(0.76f),
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (showStats) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        HeroGlassStatPill(
                            icon = Icons.Filled.Spa,
                            label = fieldsLabel,
                            value = fieldsCount,
                            tag = "hero_fields_stat",
                            onClick = onFieldsClick,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        HeroGlassStatPill(
                            icon = Icons.Filled.Eco,
                            label = areaLabel,
                            value = areaValue,
                            tag = "hero_area_stat",
                            onClick = onFieldsClick,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroGlassStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF092A16).copy(alpha = 0.68f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF42C26B).copy(alpha = 0.28f)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color(0xFF9AF5B4),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HomeMenuTileCard(
    tile: HomeMenuTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(tile.tag),
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            ModuleIconMedallion(
                route = tile.route,
                fallbackIcon = tile.icon,
                fallbackAccent = tile.accent,
                size = 46.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = tile.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 15.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 2.dp),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = extra.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                    ),
                    color = extra.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun HomeAssistantBannerCard(
    title: String,
    badgeLabel: String,
    subtitle: String,
    actionLabel: String,
    tag: String,
    onClick: () -> Unit,
) {
    val extra = LocalAqroColors.current
    val isDark = extra.isDark
    val bannerBg = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF193524), Color(0xFF15291D)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFE7F3E7), Color(0xFFF3F8F1)))
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerBg)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_assistant_orb),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isDark) Color(0xFF1E462C) else Color(0xFFD6F3DC),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF28A745)),
                            )
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isDark) Color(0xFF9DF2B0) else Color(0xFF146C2E),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF0E4B25),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTelegramBotCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    tag: String,
    onClick: () -> Unit,
) {
    val extra = LocalAqroColors.current
    val telegramBlue = Color(0xFF0088CC)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, telegramBlue.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(telegramBlue.copy(alpha = 0.13f)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = title,
                    tint = telegramBlue,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = telegramBlue.copy(alpha = 0.12f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = telegramBlue,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = telegramBlue,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBottomQuickStrip(
    weatherState: WeatherUiState,
    weatherHazards: List<WeatherHazardAlert> = emptyList(),
    onOpenWeather: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val hazardCount = if (weatherHazards.isNotEmpty()) {
        weatherHazards.size
    } else {
        listOf(
            weatherState.hasFrostRisk,
            weatherState.hasHeatRisk,
            weatherState.hasHighWind,
            weatherState.hasHeavyRain,
        ).count { it }
    }
    val noticesSub = if (hazardCount > 0) {
        String.format(strings.homeWeatherHazardCountFormat, hazardCount)
    } else {
        strings.homeWeatherSafeMsg
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("home_bottom_quick_strip"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenWeather)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = strings.homeNoticesTitle,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.homeNoticesTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = noticesSub,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hazardCount > 0) DangerRed else extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = extra.muted,
                    modifier = Modifier.size(18.dp),
                )
            }

            VerticalDivider(
                thickness = 1.dp,
                color = extra.line,
                modifier = Modifier.padding(vertical = 10.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenCalendar)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = strings.tileCalendarTitle,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.tileCalendarTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = strings.homeMonthlyCalendarSub,
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = extra.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
