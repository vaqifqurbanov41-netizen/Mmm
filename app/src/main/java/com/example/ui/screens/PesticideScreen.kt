package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomPesticide
import com.example.i18n.AppLanguage
import com.example.i18n.LocalStrings
import com.example.i18n.LocalizedPesticide
import com.example.ui.components.AqroCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.EmptyStateMessage
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.TabPillsRow
import com.example.ui.components.formatNumber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalAqroColors
import kotlinx.coroutines.launch

@Composable
fun PesticideScreen(
    language: AppLanguage,
    customPesticides: List<CustomPesticide>,
    onAddCustomPesticide: suspend (String, Double, String, Int, String, String) -> Unit,
    onDeleteCustomPesticide: suspend (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) }

    // Check Rate tab state
    var cropInput by remember { mutableStateOf("") }
    var pestNameInput by remember { mutableStateOf("") }
    var maxRateInput by remember { mutableStateOf("") }
    var planRateInput by remember { mutableStateOf("") }
    var unitIndex by remember { mutableIntStateOf(0) } // 0 = L/ha, 1 = kg/ha
    var selectedPesticideInfo by remember { mutableStateOf<LocalizedPesticide?>(null) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var checkIsViolation by remember { mutableStateOf(false) }

    // Add Custom tab state
    var newName by remember { mutableStateOf("") }
    var newMaxRate by remember { mutableStateOf("") }
    var newUnitIndex by remember { mutableIntStateOf(0) }
    var newDays by remember { mutableStateOf("20") }
    var newClassIndex by remember { mutableIntStateOf(1) } // 0=Class II, 1=Class III
    var newTarget by remember { mutableStateOf("") }
    var customFormError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomPesticide?>(null) }

    // Banned Search tab state
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

    val liveBannedMatch = remember(pestNameInput, strings.bannedPesticides) {
        val q = pestNameInput.trim().lowercase()
        if (q.length < 2) null
        else strings.bannedPesticides.firstOrNull { banned ->
            banned.name.lowercase().contains(q) ||
                banned.aliases.any { alias -> q.contains(alias.lowercase()) }
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

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            ScreenTopBar(title = strings.pestTitle, onBack = onBack)
            TabPillsRow(
                tabs = listOf(strings.tabPestCheck, strings.tabPestAdd, strings.tabPestBanned),
                selectedIndex = activeTab,
                onSelect = { activeTab = it },
            )
        }

        when (activeTab) {
            0 -> {
                item {
                    AqroCard {
                        Text(strings.pestCropLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            strings.cropSuggestions.forEach { c ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = extra.pale,
                                    border = BorderStroke(1.dp, extra.line),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { cropInput = c },
                                ) {
                                    Text(
                                        text = c,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cropInput,
                            onValueChange = { cropInput = it },
                            label = { Text(strings.pestCropLabel) },
                            placeholder = { Text(strings.pestCropPlaceholder) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("pest_crop_input"),
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(strings.pestNameLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            mergedAllowed.forEach { p ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = extra.pale,
                                    border = BorderStroke(1.dp, extra.line),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            pestNameInput = p.name
                                            maxRateInput = formatNumber(p.maxRate, 2)
                                            unitIndex = if (p.unit.startsWith("kg", true)) 1 else 0
                                            selectedPesticideInfo = p
                                        },
                                ) {
                                    Text(
                                        text = p.name.substringBefore(" ("),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pestNameInput,
                            onValueChange = {
                                pestNameInput = it
                                selectedPesticideInfo = mergedAllowed.firstOrNull { item ->
                                    item.name.equals(it.trim(), ignoreCase = true)
                                }
                            },
                            label = { Text(strings.pestNameLabel) },
                            placeholder = { Text(strings.pestNamePlaceholder) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("pest_name_input"),
                        )

                        if (liveBannedMatch != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            DangerBannerBox(
                                text = "${strings.pestBannedAlertPrefix}: ${liveBannedMatch.name}\n${liveBannedMatch.reason}\n${liveBannedMatch.legalStatus}"
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = maxRateInput,
                                onValueChange = { maxRateInput = it },
                                label = { Text(strings.pestMaxLabel) },
                                placeholder = { Text(strings.pestMaxPlaceholder) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = planRateInput,
                                onValueChange = { planRateInput = it },
                                label = { Text(strings.pestPlanLabel) },
                                placeholder = { Text(strings.pestPlanPlaceholder) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("pest_plan_rate_input"),
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
                            Text(strings.btnCheckPesticide)
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

            1 -> {
                item {
                    AqroCard {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it; customFormError = null },
                            label = { Text(strings.pestNewNameLabel) },
                            placeholder = { Text(strings.pestNewNameHint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newMaxRate,
                                onValueChange = { newMaxRate = it; customFormError = null },
                                label = { Text(strings.pestNewMaxLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = newDays,
                                onValueChange = { newDays = it },
                                label = { Text(strings.pestNewDaysLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
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
                            label = { Text(strings.pestNewTargetLabel) },
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
                            Text(strings.btnAddCustomPest)
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

            2 -> {
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
