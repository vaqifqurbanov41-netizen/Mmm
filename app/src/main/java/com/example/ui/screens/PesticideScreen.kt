package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ai.VisualThreatMatch
import com.example.data.model.CropProtectionCatalog
import com.example.data.model.CustomPesticide
import com.example.data.model.DiseaseVisualCatalog
import com.example.data.model.ThreatKind
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.i18n.LocalizedPesticide
import com.example.ui.components.AqroCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.DiseasePhotoComparisonDialog
import com.example.ui.components.EmptyStateMessage
import com.example.ui.components.RealDiseasePhotoThumbnail
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.TabPillsRow
import com.example.ui.components.formatNumber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalAqroColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PesticideScreen(
    language: AppLanguage,
    customPesticides: List<CustomPesticide>,
    onAddCustomPesticide: suspend (String, Double, String, Int, String, String) -> Unit,
    onDeleteCustomPesticide: suspend (String) -> Unit,
    onBack: () -> Unit,
    isLocalOnly: Boolean = false,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var activeTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeTab) {
        listState.scrollToItem(0)
    }

    // Tab 0: Crop -> Disease/Pest -> Active Ingredient Guide (NO commercial trade names)
    var selectedCropId by remember { mutableStateOf(CropProtectionCatalog.crops.first().id) }
    var threatFilterIndex by remember { mutableIntStateOf(0) } // 0=All, 1=Disease, 2=Pest
    var selectedThreatId by remember { mutableStateOf<String?>(CropProtectionCatalog.threatsForCrop("tomato").firstOrNull()?.id) }
    var comparedThreatMatch by remember { mutableStateOf<VisualThreatMatch?>(null) }

    // Tab 1: Separate Preparation Search & Hectare Rate Check
    var prepQuery by remember { mutableStateOf("") }
    var pestNameInput by remember { mutableStateOf("") }
    var maxRateInput by remember { mutableStateOf("") }
    var planRateInput by remember { mutableStateOf("") }
    var unitIndex by remember { mutableIntStateOf(0) } // 0 = L/ha, 1 = kg/ha
    var selectedPesticideInfo by remember { mutableStateOf<LocalizedPesticide?>(null) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var checkIsViolation by remember { mutableStateOf(false) }

    // Tab 2: Add Custom tab state (cloud only)
    var newName by remember { mutableStateOf("") }
    var newMaxRate by remember { mutableStateOf("") }
    var newUnitIndex by remember { mutableIntStateOf(0) }
    var newDays by remember { mutableStateOf("20") }
    var newClassIndex by remember { mutableIntStateOf(1) } // 0=Class II, 1=Class III
    var newTarget by remember { mutableStateOf("") }
    var customFormError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomPesticide?>(null) }

    // Tab 3: Banned Search tab state
    var bannedQuery by remember { mutableStateOf("") }

    val mergedAllowed = remember(strings.allowedPesticides, customPesticides) {
        strings.allowedPesticides + customPesticides.map { cp ->
            LocalizedPesticide(
                id = cp.id,
                name = cp.name,
                maxRate = cp.maxRate,
                unit = cp.unit,
                quarantineDays = cp.quarantineDays.toInt(),
                safetyClass = cp.safetyClass,
                target = cp.target,
            )
        }
    }

    val liveBannedMatch = remember(pestNameInput, prepQuery, strings.bannedPesticides) {
        val candidate = pestNameInput.ifBlank { prepQuery }.trim().lowercase()
        if (candidate.length < 2) null
        else strings.bannedPesticides.firstOrNull { banned ->
            banned.name.lowercase().contains(candidate) ||
                banned.aliases.any { alias -> candidate.contains(alias.lowercase()) }
        }
    }

    val cropThreats = remember(selectedCropId, threatFilterIndex) {
        CropProtectionCatalog.threatsForCrop(selectedCropId).filter { threat ->
            when (threatFilterIndex) {
                1 -> threat.kind == ThreatKind.DISEASE
                2 -> threat.kind == ThreatKind.PEST
                else -> true
            }
        }
    }

    val searchedPreparations = remember(prepQuery) {
        CropProtectionCatalog.searchPreparations(prepQuery)
    }

    val searchedCustomPreparations = remember(prepQuery, customPesticides) {
        val q = CropProtectionCatalog.normalizeSearch(prepQuery)
        if (q.isBlank()) customPesticides
        else customPesticides.filter {
            CropProtectionCatalog.normalizeSearch(it.name).contains(q) ||
                CropProtectionCatalog.normalizeSearch(it.target).contains(q)
        }
    }

    ConfirmDeleteDialog(
        visible = pendingDelete != null,
        itemLabel = pendingDelete?.name.orEmpty(),
        onConfirm = {
            val item = pendingDelete
            pendingDelete = null
            if (item != null && item.id.isNotBlank()) {
                scope.launch { onDeleteCustomPesticide(item.id) }
            }
        },
        onDismiss = { pendingDelete = null },
    )

    comparedThreatMatch?.let { match ->
        DiseasePhotoComparisonDialog(
            threat = match.threat,
            profile = match.profile,
            scannedBitmap = null,
            similarityPct = null,
            language = language,
            onDismiss = { comparedThreatMatch = null },
        )
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("pesticide_screen"),
    ) {
        item {
            ScreenTopBar(title = strings.pestTitle, onBack = onBack)
            val tabs = if (isLocalOnly) {
                listOf(strings.tabPestGuide, strings.tabPestSearch, strings.tabPestBanned)
            } else {
                listOf(strings.tabPestGuide, strings.tabPestSearch, strings.tabPestAdd, strings.tabPestBanned)
            }
            TabPillsRow(
                tabs = tabs,
                selectedIndex = activeTab.coerceIn(0, tabs.lastIndex),
                onSelect = { activeTab = it },
            )
        }

        val resolvedTab = if (isLocalOnly && activeTab == 2) 3 else activeTab
        when (resolvedTab) {
            // 0: Crop -> Disease / Pest -> Active Ingredient Only
            0 -> {
                item {
                    AqroCard {
                        Text(
                            text = strings.pestStepCrop,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CropProtectionCatalog.crops.forEach { crop ->
                                val selected = crop.id == selectedCropId
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedCropId = crop.id
                                        threatFilterIndex = 0
                                        selectedThreatId = CropProtectionCatalog.threatsForCrop(crop.id).firstOrNull()?.id
                                    },
                                    label = { Text(crop.name.get(language)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier.testTag("pest_crop_chip_${crop.id}"),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.pestStepThreat,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        ) {
                            val filterItems = listOf(
                                Triple(0, strings.pestFilterAll, "pest_filter_all"),
                                Triple(1, strings.pestFilterDiseases, "pest_filter_disease"),
                                Triple(2, strings.pestFilterPests, "pest_filter_pest"),
                            )
                            filterItems.forEach { (idx, label, tag) ->
                                val active = threatFilterIndex == idx
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) MaterialTheme.colorScheme.primary else extra.pale,
                                    border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else extra.line),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            threatFilterIndex = idx
                                            val matching = CropProtectionCatalog.threatsForCrop(selectedCropId).filter { t ->
                                                when (idx) {
                                                    1 -> t.kind == ThreatKind.DISEASE
                                                    2 -> t.kind == ThreatKind.PEST
                                                    else -> true
                                                }
                                            }
                                            if (matching.none { it.id == selectedThreatId }) {
                                                selectedThreatId = matching.firstOrNull()?.id
                                            }
                                        }
                                        .testTag(tag),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (cropThreats.isEmpty()) {
                    item {
                        AqroCard {
                            EmptyStateMessage(icon = Icons.Filled.BugReport, message = strings.bannedEmptySearch)
                        }
                    }
                } else {
                    items(cropThreats, key = { it.id }) { threat ->
                        val isSelected = selectedThreatId == threat.id
                        val kindLabel = if (threat.kind == ThreatKind.DISEASE) {
                            strings.pestFilterDiseases
                        } else {
                            strings.pestFilterPests
                        }
                        AqroCard(
                            containerColor = if (isSelected) extra.pale else MaterialTheme.colorScheme.surface,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else extra.line,
                            modifier = Modifier.testTag("pest_threat_card_${threat.id}"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedThreatId = if (isSelected) null else threat.id
                                    },
                            ) {
                                Text(
                                    text = threat.name.get(language),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        text = kindLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val visualProfile = DiseaseVisualCatalog.profileFor(threat.id)
                                if (visualProfile != null) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, extra.line),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                comparedThreatMatch = VisualThreatMatch(
                                                    threat = threat,
                                                    profile = visualProfile,
                                                    similarityPct = 100,
                                                )
                                            }
                                            .testTag("pest_real_photo_preview_${threat.id}"),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(8.dp),
                                        ) {
                                            RealDiseasePhotoThumbnail(
                                                imageUrl = DiseaseVisualCatalog.resizedUrl(visualProfile.referenceImageUrl, 360),
                                                contentDescription = threat.name.get(language),
                                                size = 76.dp,
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${DiseaseVisualCatalog.patternLabel(visualProfile.patternType, language)} • ${visualProfile.plantPart.get(language)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = visualProfile.visualCue.get(language),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Compare,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(13.dp),
                                                    )
                                                    Text(
                                                        text = "${strings.scannerRealPhotoCompareBtn} • ${visualProfile.imageAttribution}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                Text(
                                    text = strings.pestSymptomsLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = extra.muted,
                                )
                                Text(
                                    text = threat.symptoms.get(language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pest_active_ingredient_box_${threat.id}"),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Science,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = strings.pestActiveIngredientLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.ExtraBold,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = threat.activeIngredient.get(language),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.testTag("pest_active_ingredient_text_${threat.id}"),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = strings.pestGuidanceLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = extra.muted,
                                )
                                Text(
                                    text = threat.guidance.get(language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        prepQuery = threat.searchIngredientKey
                                        activeTab = 1
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pest_find_prep_by_ingredient_btn"),
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(
                                        text = strings.pestFindPrepByIngredientBtn,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 1: Separate Preparation Search & Hectare Dose Verification
            1 -> {
                item {
                    AqroCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalPharmacy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = strings.prepSearchTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = prepQuery,
                            onValueChange = { prepQuery = it },
                            label = { Text(strings.prepSearchLabel) },
                            placeholder = { Text(strings.prepSearchPlaceholder) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (prepQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = { prepQuery = "" },
                                        modifier = Modifier.testTag("prep_search_clear_btn"),
                                    ) {
                                        Icon(Icons.Filled.Clear, contentDescription = strings.clear)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("prep_search_input"),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val quickIngredients = listOf(
                            "Mankozeb",
                            "Asetamiprid",
                            "Azoksistrobin",
                            "Emamektin",
                            "Abamektin",
                            "Tiametoksam",
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            quickIngredients.forEach { ing ->
                                val active = prepQuery.equals(ing, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (active) MaterialTheme.colorScheme.primary else extra.pale,
                                    border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else extra.line),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { prepQuery = if (active) "" else ing },
                                ) {
                                    Text(
                                        text = ing,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }

                        if (liveBannedMatch != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            DangerBannerBox(
                                text = "${strings.pestBannedAlertPrefix}: ${liveBannedMatch.name}\n${liveBannedMatch.reason}\n${liveBannedMatch.legalStatus}"
                            )
                        }
                    }
                }

                if (searchedPreparations.isEmpty() && searchedCustomPreparations.isEmpty()) {
                    item {
                        AqroCard {
                            EmptyStateMessage(icon = Icons.Filled.Search, message = strings.prepEmptySearch)
                        }
                    }
                } else {
                    items(searchedPreparations, key = { it.id }) { prep ->
                        AqroCard(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .clickable {
                                    pestNameInput = "${prep.tradeName} (${prep.activeIngredient.get(language)})"
                                    maxRateInput = formatNumber(prep.maxRate, 2)
                                    unitIndex = if (prep.unit.startsWith("kg", true)) 1 else 0
                                    selectedPesticideInfo = LocalizedPesticide(
                                        id = prep.id,
                                        name = prep.tradeName,
                                        maxRate = prep.maxRate,
                                        unit = prep.unit,
                                        quarantineDays = prep.quarantineDays,
                                        safetyClass = prep.safetyClass.get(language),
                                        target = prep.target.get(language),
                                    )
                                }
                                .testTag("prep_card_${prep.id}"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = prep.tradeName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "${strings.pestActiveIngredientLabel}: ${prep.activeIngredient.get(language)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = prep.target.get(language),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.muted,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = extra.pale,
                                    border = BorderStroke(1.dp, extra.line),
                                ) {
                                    Text(
                                        text = "${formatNumber(prep.maxRate, 2)} ${prep.unit}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    items(searchedCustomPreparations, key = { "custom_${it.id}" }) { cp ->
                        AqroCard(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .clickable {
                                    pestNameInput = cp.name
                                    maxRateInput = formatNumber(cp.maxRate, 2)
                                    unitIndex = if (cp.unit.startsWith("kg", true)) 1 else 0
                                    selectedPesticideInfo = LocalizedPesticide(
                                        id = cp.id,
                                        name = cp.name,
                                        maxRate = cp.maxRate,
                                        unit = cp.unit,
                                        quarantineDays = cp.quarantineDays.toInt(),
                                        safetyClass = cp.safetyClass,
                                        target = cp.target,
                                    )
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(cp.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (cp.target.isNotBlank()) {
                                        Text(cp.target, style = MaterialTheme.typography.bodySmall, color = extra.muted)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = extra.pale,
                                ) {
                                    Text(
                                        text = "${formatNumber(cp.maxRate, 2)} ${cp.unit}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Hectare Dose & Quarantine Safety Verification Card
                item {
                    AqroCard {
                        Text(
                            text = strings.prepRateSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.prepRateSectionSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pestNameInput,
                            onValueChange = {
                                pestNameInput = it
                                selectedPesticideInfo = mergedAllowed.firstOrNull { item ->
                                    item.name.equals(it.trim(), ignoreCase = true)
                                }
                            },
                            label = { Text(strings.pestNameLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text(strings.pestNamePlaceholder) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("pest_name_input"),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        ) {
                            OutlinedTextField(
                                value = maxRateInput,
                                onValueChange = { maxRateInput = it },
                                label = { Text(strings.pestMaxLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text(strings.pestMaxPlaceholder) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            OutlinedTextField(
                                value = planRateInput,
                                onValueChange = { planRateInput = it },
                                label = { Text(strings.pestPlanLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text(strings.pestPlanPlaceholder) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("pest_plan_rate_input"),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(strings.pestUnitLabel, style = MaterialTheme.typography.labelMedium, color = extra.muted)
                        Spacer(modifier = Modifier.height(4.dp))
                        TabPillsRow(
                            tabs = listOf("L/ha", "kg/ha"),
                            selectedIndex = unitIndex,
                            onSelect = { unitIndex = it },
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (liveBannedMatch != null) {
                                    checkIsViolation = true
                                    checkResult = "${strings.pestBannedResultTitle}\n${liveBannedMatch.name}: ${liveBannedMatch.reason}"
                                    return@Button
                                }
                                val matched = selectedPesticideInfo ?: mergedAllowed.firstOrNull {
                                    it.name.contains(pestNameInput.trim(), ignoreCase = true)
                                }
                                val maxVal = maxRateInput.replace(",", ".").toDoubleOrNull() ?: matched?.maxRate
                                val planVal = planRateInput.replace(",", ".").toDoubleOrNull()
                                val unitLabel = if (unitIndex == 1) "kg/ha" else "L/ha"
                                if (pestNameInput.isBlank() || maxVal == null || planVal == null || planVal <= 0) {
                                    checkIsViolation = false
                                    checkResult = strings.invalidInput
                                } else {
                                    val safe = planVal <= maxVal
                                    checkIsViolation = !safe
                                    val header = if (safe) {
                                        String.format(
                                            language.locale,
                                            strings.pestRateSafe,
                                            formatNumber(planVal, 2),
                                            unitLabel,
                                            formatNumber(maxVal, 2),
                                            unitLabel,
                                        )
                                    } else {
                                        String.format(
                                            language.locale,
                                            strings.pestRateExceeded,
                                            formatNumber(planVal, 2),
                                            unitLabel,
                                            formatNumber(maxVal, 2),
                                            unitLabel,
                                        )
                                    }
                                    val meta = matched?.let {
                                        String.format(
                                            language.locale,
                                            strings.pestMetaFormat,
                                            formatNumber(it.maxRate, 2),
                                            it.unit,
                                            it.quarantineDays,
                                            it.safetyClass,
                                            it.target,
                                        )
                                    }
                                    checkResult = if (meta != null) "$header\n\n$meta" else header
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("check_pesticide_btn"),
                        ) {
                            Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(strings.btnCheckPesticide, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                item {
                    if (checkIsViolation && checkResult != null) {
                        DangerBannerBox(text = checkResult!!)
                    } else {
                        AqroCard(containerColor = extra.pale) {
                            Text(
                                text = checkResult ?: strings.pestEmptyOutput,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (checkResult != null) FontWeight.Bold else FontWeight.Normal,
                                color = if (checkResult != null) MaterialTheme.colorScheme.onSurface else extra.muted,
                                modifier = Modifier.testTag("pest_check_result"),
                            )
                        }
                    }
                }
            }

            // 2: Add Custom Preparation (Cloud only)
            2 -> {
                item {
                    AqroCard {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it; customFormError = null },
                            label = { Text(strings.pestNewNameLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text(strings.pestNewNameHint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        ) {
                            OutlinedTextField(
                                value = newMaxRate,
                                onValueChange = { newMaxRate = it; customFormError = null },
                                label = { Text(strings.pestNewMaxLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            OutlinedTextField(
                                value = newDays,
                                onValueChange = { newDays = it },
                                label = { Text(strings.pestNewDaysLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(strings.pestNewUnitLabel, style = MaterialTheme.typography.labelMedium, color = extra.muted)
                        Spacer(modifier = Modifier.height(4.dp))
                        TabPillsRow(
                            tabs = listOf("L/ha", "kg/ha"),
                            selectedIndex = newUnitIndex,
                            onSelect = { newUnitIndex = it },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(strings.pestNewClassLabel, style = MaterialTheme.typography.labelMedium, color = extra.muted)
                        Spacer(modifier = Modifier.height(4.dp))
                        TabPillsRow(
                            tabs = listOf(strings.pestClass2, strings.pestClass3),
                            selectedIndex = newClassIndex,
                            onSelect = { newClassIndex = it },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newTarget,
                            onValueChange = { newTarget = it },
                            label = { Text(strings.pestNewTargetLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text(strings.pestNewTargetHint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (customFormError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            DangerBannerBox(text = customFormError!!)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val rate = newMaxRate.replace(",", ".").toDoubleOrNull()
                                val days = newDays.toIntOrNull() ?: 14
                                if (newName.isBlank() || rate == null || rate <= 0) {
                                    customFormError = strings.fillRequiredFields
                                } else {
                                    val unitStr = if (newUnitIndex == 1) "kg/ha" else "L/ha"
                                    val clsStr = if (newClassIndex == 0) strings.pestClass2 else strings.pestClass3
                                    scope.launch {
                                        onAddCustomPesticide(newName, rate, unitStr, days, clsStr, newTarget)
                                        newName = ""
                                        newMaxRate = ""
                                        newTarget = ""
                                        customFormError = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("add_custom_pest_btn"),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(strings.btnAddCustomPest, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (customPesticides.isEmpty()) {
                    item {
                        AqroCard {
                            EmptyStateMessage(icon = Icons.Filled.Science, message = strings.customPestEmpty)
                        }
                    }
                } else {
                    items(customPesticides, key = { it.id }) { cp ->
                        AqroCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cp.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${formatNumber(cp.maxRate, 2)} ${cp.unit} • ${cp.quarantineDays} d • ${cp.safetyClass}" +
                                            if (cp.target.isNotBlank()) " • ${cp.target}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extra.muted,
                                    )
                                }
                                IconButton(onClick = { pendingDelete = cp }) {
                                    Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = DangerRed)
                                }
                            }
                        }
                    }
                }
            }

            // 3: Banned Active Ingredients List
            3 -> {
                item {
                    OutlinedTextField(
                        value = bannedQuery,
                        onValueChange = { bannedQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(strings.bannedSearchHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("banned_search_input"),
                    )
                }

                val filtered = strings.bannedPesticides.filter { item ->
                    val q = bannedQuery.trim().lowercase()
                    q.isBlank() ||
                        item.name.lowercase().contains(q) ||
                        item.reason.lowercase().contains(q) ||
                        item.aliases.any { it.lowercase().contains(q) }
                }

                if (filtered.isEmpty()) {
                    item {
                        AqroCard {
                            EmptyStateMessage(icon = Icons.Filled.Search, message = strings.bannedEmptySearch)
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { banned ->
                        AqroCard(
                            containerColor = extra.dangerContainer,
                            borderColor = DangerRed.copy(alpha = 0.4f),
                        ) {
                            Text(
                                text = banned.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = extra.onDangerContainer,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${strings.bannedReasonLabel}: ${banned.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${strings.bannedLegalLabel}: ${banned.legalStatus}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DangerRed,
                            )
                        }
                    }
                }
            }
        }
    }
}
