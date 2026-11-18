package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import com.example.data.model.AdminContentItem
import com.example.data.model.FarmField
import com.example.data.model.isAdminEmail
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.KTN_TELEGRAM_BOT_URL
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
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    var showCustomizeDialog by remember { mutableStateOf(false) }

    val totalHa = fields.sumOf { it.sizeHa }
    val showAdminTile = isAdminEmail(userEmail)
    val adminNotices = adminEntries.filter { it.category == "notice" }

    val allTiles = buildList {
        add(HomeMenuTile(NavRoutes.NPK, strings.tileNpkTitle, strings.tileNpkSub, Icons.Filled.Grass, EmeraldPrimary, "tile_npk"))
        add(HomeMenuTile(NavRoutes.WEATHER, strings.tileWeatherTitle, strings.tileWeatherSub, Icons.Filled.WbSunny, Color(0xFF2F80ED), "tile_weather"))
        add(HomeMenuTile(NavRoutes.PESTICIDE, strings.tilePestTitle, strings.tilePestSub, Icons.Filled.Science, WarningAmber, "tile_pesticide"))
        add(HomeMenuTile(NavRoutes.FIELDS, strings.tileFieldsTitle, strings.tileFieldsSub, Icons.Filled.Map, Color(0xFF1F8A70), "tile_fields"))
        add(HomeMenuTile(NavRoutes.AGRO_CALENDAR, strings.tileCalendarTitle, strings.tileCalendarSub, Icons.Filled.CalendarMonth, Color(0xFF7E22CE), "tile_calendar"))
        add(HomeMenuTile(NavRoutes.INSURANCE, strings.tileInsuranceTitle, strings.tileInsuranceSub, Icons.Filled.VerifiedUser, Color(0xFF0369A1), "tile_insurance"))
        add(HomeMenuTile(NavRoutes.STATIONS, strings.tileStationsTitle, strings.tileStationsSub, Icons.Filled.Storefront, Color(0xFF0D9488), "tile_stations"))
        add(HomeMenuTile(NavRoutes.SUBSIDY, strings.tileSubsidyTitle, strings.tileSubsidySub, Icons.Filled.RequestQuote, PhosphorusAmber, "tile_subsidy"))
        add(HomeMenuTile(NavRoutes.EQUIPMENT, strings.tileEquipmentTitle, strings.tileEquipmentSub, Icons.Filled.Agriculture, PotassiumPlum, "tile_equipment"))
        add(HomeMenuTile(NavRoutes.FINANCE, strings.tileFinanceTitle, strings.tileFinanceSub, Icons.Filled.AccountBalanceWallet, Color(0xFF0F766E), "tile_finance"))
        add(HomeMenuTile(NavRoutes.HARVEST, strings.tileHarvestTitle, strings.tileHarvestSub, Icons.Filled.Spa, EmeraldBright, "tile_harvest"))
        add(HomeMenuTile(NavRoutes.GALLERY, strings.tileGalleryTitle, strings.tileGallerySub, Icons.Filled.PhotoCamera, Color(0xFF0284C7), "tile_gallery"))
        add(HomeMenuTile(NavRoutes.SEEDS, strings.tileSeedsTitle, strings.tileSeedsSub, Icons.Filled.LocalFlorist, Color(0xFF15803D), "tile_seeds"))
        add(HomeMenuTile(NavRoutes.LINKS, strings.tileLinksTitle, strings.tileLinksSub, Icons.Filled.Link, Color(0xFF4F46E5), "tile_links"))
        if (showAdminTile) {
            add(HomeMenuTile(NavRoutes.ADMIN, strings.tileAdminTitle, strings.tileAdminSub, Icons.Filled.AdminPanelSettings, Color(0xFFB45309), "tile_admin"))
        }
    }
    val tiles = allTiles.filter { it.route == NavRoutes.ADMIN || it.route !in hiddenModules }

    ModuleVisibilityDialog(
        visible = showCustomizeDialog,
        isLocalOnly = false,
        hiddenModules = hiddenModules,
        onToggleModule = onToggleModuleVisible,
        onShowAll = onResetModules,
        onDismiss = { showCustomizeDialog = false },
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().testTag("home_screen_list"),
    ) {
        // Hero Farmer Panel Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))
                        .padding(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.heroTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = strings.heroSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onNavigate(NavRoutes.FIELDS) }
                                    .testTag("hero_fields_stat"),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = strings.myFieldsStat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = fields.size.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onNavigate(NavRoutes.FIELDS) }
                                    .testTag("hero_area_stat"),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = strings.totalAreaStat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${formatNumber(totalHa, 2)} ha",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // AI Agronomist Feature Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigate(NavRoutes.CHAT) }
                    .testTag("home_ai_banner"),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(extra.pale),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = strings.aiBannerTitle,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.aiBannerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = strings.aiBannerSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = extra.pale,
                        ) {
                            Text(
                                text = strings.aiLiveBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = strings.open,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // KTN ASI Telegram Bot Card
        item {
            val telegramBlue = Color(0xFF0088CC)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, telegramBlue.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { openExternalUrl(context, KTN_TELEGRAM_BOT_URL) }
                    .testTag("home_telegram_bot_card"),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(telegramBlue.copy(alpha = 0.14f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = strings.telegramBotTitle,
                            tint = telegramBlue,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.telegramBotTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = strings.telegramBotSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = telegramBlue.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = strings.telegramBotBtn,
                            style = MaterialTheme.typography.labelMedium,
                            color = telegramBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // Weather Hazard Alert Card (only shown when frost, high wind, or heavy rain is detected)
        if (weatherState.hasAnyHazard) {
            item {
                val alertTitle = strings.alertWeatherTitle
                val alertSubtitle = buildList {
                    if (weatherState.hasFrostRisk) add(strings.frostWarning)
                    if (weatherState.hasHighWind) add(strings.windWarning)
                    if (weatherState.hasHeavyRain) add(strings.rainWarning)
                }.joinToString(" • ")

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

        // Adaptive Menu Grid
        item {
            if (tiles.isEmpty()) {
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
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                rowItems.forEach { tile ->
                                    HomeMenuTileCard(
                                        tile = tile,
                                        onClick = { onNavigate(tile.route) },
                                        modifier = Modifier.weight(1f),
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .height(114.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(tile.tag),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tile.accent.copy(alpha = 0.14f)),
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = tile.title,
                    tint = tile.accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
