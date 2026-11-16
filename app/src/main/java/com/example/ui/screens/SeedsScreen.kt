package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AgriRegion
import com.example.data.model.SeedCatalog
import com.example.data.model.SeedVariety
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.components.ScreenTopBar
import com.example.ui.theme.LocalAqroColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeedsScreen(
    language: AppLanguage,
    onBack: () -> Unit,
    onAskAiAboutVariety: ((String) -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current

    var selectedRegionId by remember { mutableStateOf(SeedCatalog.regions.first().id) }
    val selectedRegion: AgriRegion = remember(selectedRegionId) {
        SeedCatalog.regions.firstOrNull { it.id == selectedRegionId } ?: SeedCatalog.regions.first()
    }
    val regionCrops = remember(selectedRegionId) { SeedCatalog.cropsForRegion(selectedRegionId) }
    var selectedCropId by remember { mutableStateOf(regionCrops.firstOrNull()?.id ?: "wheat") }

    val effectiveCropId = if (regionCrops.any { it.id == selectedCropId }) {
        selectedCropId
    } else {
        regionCrops.firstOrNull()?.id.orEmpty()
    }

    val recommendedVarieties = remember(selectedRegionId, effectiveCropId) {
        SeedCatalog.varietiesFor(selectedRegionId, effectiveCropId)
    }

    var detailVariety by remember { mutableStateOf<SeedVariety?>(null) }

    fun shareVariety(variety: SeedVariety) {
        val cropName = SeedCatalog.crops.firstOrNull { it.id == variety.cropId }?.name?.get(language).orEmpty()
        val text = buildString {
            appendLine("${strings.appName} — ${variety.name} ($cropName)")
            appendLine("${strings.seedRegionsLabel}: ${selectedRegion.name.get(language)}")
            appendLine("${strings.seedOriginLabel}: ${variety.origin.get(language)}")
            appendLine("${strings.seedSowingTimeLabel}: ${variety.sowingWindow.get(language)}")
            appendLine("${strings.seedSeedingRateLabel}: ${variety.seedingRate.get(language)}")
            appendLine("${strings.seedYieldLabel}: ${variety.yieldPotential.get(language)}")
            appendLine("${strings.seedResistanceLabel}: ${variety.resistance.get(language)}")
            append("${strings.seedAgronomyTipLabel}: ${variety.agronomyTip.get(language)}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, variety.name)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, strings.seedShareBtn))
    }

    detailVariety?.let { variety ->
        SeedVarietyDetailDialog(
            variety = variety,
            language = language,
            onDismiss = { detailVariety = null },
            onShare = { shareVariety(variety) },
            onAskAi = onAskAiAboutVariety?.let { askCallback ->
                {
                    detailVariety = null
                    val cropName = SeedCatalog.crops.firstOrNull { it.id == variety.cropId }?.name?.get(language).orEmpty()
                    val prompt = when (language) {
                        AppLanguage.AZ -> "${selectedRegion.name.az} bölgəsində ${variety.name} ($cropName) sortunun səpini, suvarılması və gübrələnməsi haqqında ətraflı məsləhət ver."
                        AppLanguage.EN -> "Give detailed planting, irrigation, and fertilizing guidance for ${variety.name} ($cropName) in the ${selectedRegion.name.en} region."
                        AppLanguage.RU -> "Дай подробные рекомендации по посеву, поливу и подкормке сорта ${variety.name} ($cropName) для региона ${selectedRegion.name.ru}."
                    }
                    askCallback(prompt)
                }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("seeds_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.seedsScreenTitle, onBack = onBack)
        }

        // Step 1: Select Agricultural Region
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, extra.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = strings.seedsStepRegion,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        SeedCatalog.regions.forEach { region ->
                            val selected = region.id == selectedRegionId
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedRegionId = region.id
                                    val available = SeedCatalog.cropsForRegion(region.id)
                                    if (available.none { it.id == selectedCropId } && available.isNotEmpty()) {
                                        selectedCropId = available.first().id
                                    }
                                },
                                label = {
                                    Text(
                                        text = region.name.get(language),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.testTag("seed_region_chip_${region.id}"),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = extra.pale,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(10.dp),
                        ) {
                            Text(
                                text = selectedRegion.districts.get(language),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = selectedRegion.climateNote.get(language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Step 2: Select Crop
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, extra.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = strings.seedsStepCrop,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        regionCrops.forEach { crop ->
                            val selected = crop.id == effectiveCropId
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCropId = crop.id },
                                label = {
                                    Text(
                                        text = crop.name.get(language),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.testTag("seed_crop_chip_${crop.id}"),
                            )
                        }
                    }
                }
            }
        }

        // Step 3 Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    text = strings.seedsRecommendedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = extra.pale,
                ) {
                    Text(
                        text = String.format(language.locale, strings.seedsCountFormat, recommendedVarieties.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Recommended Variety Cards
        items(recommendedVarieties, key = { it.id }) { variety ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { detailVariety = variety }
                    .testTag("seed_variety_card_${variety.id}"),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = variety.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = extra.pale,
                        ) {
                            Text(
                                text = variety.badge.get(language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Text(
                        text = variety.summary.get(language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = extra.muted,
                    )

                    HorizontalDivider(color = extra.line)

                    VarietyQuickStatRow(label = strings.seedYieldLabel, value = variety.yieldPotential.get(language))
                    VarietyQuickStatRow(label = strings.seedSowingTimeLabel, value = variety.sowingWindow.get(language))
                    VarietyQuickStatRow(label = strings.seedSeedingRateLabel, value = variety.seedingRate.get(language))

                    OutlinedButton(
                        onClick = { detailVariety = variety },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("seed_detail_btn_${variety.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(18.dp),
                        )
                        Text(
                            text = strings.seedDetailBtn,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VarietyQuickStatRow(
    label: String,
    value: String,
) {
    val extra = LocalAqroColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = extra.muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SeedVarietyDetailDialog(
    variety: SeedVariety,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onAskAi: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val cropName = SeedCatalog.crops.firstOrNull { it.id == variety.cropId }?.name?.get(language).orEmpty()
    val regionNames = SeedCatalog.regions
        .filter { it.id in variety.regionIds }
        .joinToString(", ") { it.name.get(language) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = variety.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (cropName.isNotBlank()) {
                    Text(
                        text = "$cropName • ${variety.badge.get(language)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("seed_detail_dialog"),
            ) {
                DetailField(strings.seedOriginLabel, variety.origin.get(language))
                DetailField(strings.seedRegionsLabel, regionNames)
                DetailField(strings.seedSowingTimeLabel, variety.sowingWindow.get(language))
                DetailField(strings.seedSeedingRateLabel, variety.seedingRate.get(language))
                DetailField(strings.seedVegetationLabel, variety.vegetationPeriod.get(language))
                DetailField(strings.seedYieldLabel, variety.yieldPotential.get(language))
                DetailField(strings.seedResistanceLabel, variety.resistance.get(language))
                DetailField(strings.seedSoilIrrigationLabel, variety.soilAndIrrigation.get(language))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extra.pale,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(10.dp),
                    ) {
                        Text(
                            text = strings.seedAgronomyTipLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = variety.agronomyTip.get(language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (onAskAi != null) {
                    Button(
                        onClick = onAskAi,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("seed_ask_ai_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(18.dp),
                        )
                        Text(
                            text = strings.seedAskAiBtn,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShare, modifier = Modifier.testTag("seed_share_btn")) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp).size(16.dp),
                )
                Text(strings.seedShareBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("seed_detail_close_btn")) {
                Text(strings.back)
            }
        },
    )
}

@Composable
private fun DetailField(label: String, value: String) {
    val extra = LocalAqroColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = extra.muted,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
