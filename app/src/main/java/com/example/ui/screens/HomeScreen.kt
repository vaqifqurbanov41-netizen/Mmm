package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FarmField
import com.example.i18n.LocalStrings
import com.example.ui.components.formatNumber
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
import com.example.ui.viewmodel.EquipmentStatus
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
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current

    val totalHa = fields.sumOf { it.sizeHa }

    val tiles = listOf(
        HomeMenuTile(NavRoutes.NPK, strings.tileNpkTitle, strings.tileNpkSub, Icons.Filled.Grass, EmeraldPrimary, "tile_npk"),
        HomeMenuTile(NavRoutes.WEATHER, strings.tileWeatherTitle, strings.tileWeatherSub, Icons.Filled.WbSunny, Color(0xFF2F80ED), "tile_weather"),
        HomeMenuTile(NavRoutes.PESTICIDE, strings.tilePestTitle, strings.tilePestSub, Icons.Filled.Science, WarningAmber, "tile_pesticide"),
        HomeMenuTile(NavRoutes.FIELDS, strings.tileFieldsTitle, strings.tileFieldsSub, Icons.Filled.Map, Color(0xFF1F8A70), "tile_fields"),
        HomeMenuTile(NavRoutes.SUBSIDY, strings.tileSubsidyTitle, strings.tileSubsidySub, Icons.Filled.RequestQuote, PhosphorusAmber, "tile_subsidy"),
        HomeMenuTile(NavRoutes.EQUIPMENT, strings.tileEquipmentTitle, strings.tileEquipmentSub, Icons.Filled.Agriculture, PotassiumPlum, "tile_equipment"),
        HomeMenuTile(NavRoutes.FINANCE, strings.tileFinanceTitle, strings.tileFinanceSub, Icons.Filled.AccountBalanceWallet, Color(0xFF0F766E), "tile_finance"),
        HomeMenuTile(NavRoutes.HARVEST, strings.tileHarvestTitle, strings.tileHarvestSub, Icons.Filled.Spa, EmeraldBright, "tile_harvest"),
        HomeMenuTile(NavRoutes.LINKS, strings.tileLinksTitle, strings.tileLinksSub, Icons.Filled.Link, Color(0xFF4F46E5), "tile_links"),
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
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
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigate(NavRoutes.FIELDS) }
                                    .testTag("hero_fields_stat"),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = strings.myFieldsStat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                    Text(
                                        text = fields.size.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigate(NavRoutes.FIELDS) }
                                    .testTag("hero_area_stat"),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = strings.totalAreaStat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                    Text(
                                        text = "${formatNumber(totalHa, 2)} ha",
                                        style = MaterialTheme.typography.headlineSmall,
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

        // Smart Hazard / Equipment Alert Card
        item {
            val hasWeatherHazard = weatherState.hasAnyHazard
            val hasOverdueEq = equipmentAlerts.any { it.status == EquipmentStatus.OVERDUE }
            val hasEqAlert = equipmentAlerts.isNotEmpty()

            val bgColor = when {
                hasWeatherHazard || hasOverdueEq -> extra.dangerContainer
                hasEqAlert -> extra.warningContainer
                else -> MaterialTheme.colorScheme.surface
            }
            val borderColor = when {
                hasWeatherHazard || hasOverdueEq -> DangerBorder
                hasEqAlert -> WarningAmber.copy(alpha = 0.5f)
                else -> extra.line
            }
            val iconColor = when {
                hasWeatherHazard || hasOverdueEq -> DangerRed
                hasEqAlert -> WarningAmber
                else -> MaterialTheme.colorScheme.primary
            }

            val alertTitle: String
            val alertSubtitle: String
            val alertRoute: String
            when {
                hasWeatherHazard -> {
                    alertTitle = strings.alertWeatherTitle
                    alertSubtitle = buildList {
                        if (weatherState.hasFrostRisk) add(strings.frostWarning)
                        if (weatherState.hasHighWind) add(strings.windWarning)
                        if (weatherState.hasHeavyRain) add(strings.rainWarning)
                    }.joinToString(" • ")
                    alertRoute = NavRoutes.WEATHER
                }
                hasEqAlert -> {
                    alertTitle = strings.alertEquipmentTitle
                    alertSubtitle = equipmentAlerts.joinToString(", ") {
                        val badge = if (it.status == EquipmentStatus.OVERDUE) strings.eqStatusOverdue else strings.eqStatusSoon
                        "${it.itemName} ($badge)"
                    }
                    alertRoute = NavRoutes.EQUIPMENT
                }
                else -> {
                    alertTitle = strings.alertNormalTitle
                    alertSubtitle = strings.alertNormalSub
                    alertRoute = NavRoutes.EQUIPMENT
                }
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onNavigate(alertRoute) }
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
                            .background(iconColor.copy(alpha = 0.14f)),
                    ) {
                        Icon(
                            imageVector = when {
                                hasWeatherHazard || hasOverdueEq -> Icons.Filled.Warning
                                hasEqAlert -> Icons.Filled.NotificationsActive
                                else -> Icons.Filled.CheckCircle
                            },
                            contentDescription = alertTitle,
                            tint = iconColor,
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
                            color = if (hasWeatherHazard || hasOverdueEq) extra.onDangerContainer else extra.muted,
                        )
                    }
                }
            }
        }

        // 9-Tile Adaptive Menu Grid
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
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
