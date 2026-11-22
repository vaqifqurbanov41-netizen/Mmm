package com.aistudio.texnonezaret.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.texnonezaret.data.ThemeMode
import com.aistudio.texnonezaret.domain.ColumnField
import com.aistudio.texnonezaret.domain.InspectionStatusKind
import com.aistudio.texnonezaret.domain.StatusFilter
import com.aistudio.texnonezaret.domain.TexnonezaretEngine
import com.aistudio.texnonezaret.domain.VehicleRow
import com.aistudio.texnonezaret.ui.theme.LocalTexnoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainSearchScreen(
    state: SearchUiState,
    securityState: SecurityUiState,
    effects: SharedFlow<InterventionUiEffect>,
    onImportSpreadsheet: (Uri) -> Unit,
    onRequestProtectedAction: (PendingInterventionAction) -> Unit,
    onLockInterventionMode: () -> Unit,
    onOpenSecurityCenter: () -> Unit,
    onToggleColumnMapExpanded: () -> Unit,
    onPlateQueryChange: (String) -> Unit,
    onNameQueryChange: (String) -> Unit,
    onSelectStatusFilter: (StatusFilter) -> Unit,
    onCycleThemeMode: () -> Unit,
    onLockApp: () -> Unit,
    onBuildBackupJson: () -> String?,
    onImportBackupUri: (Uri) -> Unit,
) {
    val extra = LocalTexnoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localMessage by remember { mutableStateOf<String?>(null) }

    val spreadsheetPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            localMessage = null
            onImportSpreadsheet(uri)
        }
    }

    val backupImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            localMessage = null
            onImportBackupUri(uri)
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val json = onBuildBackupJson() ?: return@withContext false
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                        true
                    }.getOrDefault(false)
                }
                localMessage = if (ok) "İmzalı ehtiyat nüsxə saxlanıldı." else "Ehtiyat nüsxə saxlanıla bilmədi."
            }
        }
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                InterventionUiEffect.LaunchExcelPicker -> spreadsheetPicker.launch(arrayOf("*/*"))
                InterventionUiEffect.LaunchBackupExport -> backupExportLauncher.launch("tnt_backup.json")
                InterventionUiEffect.LaunchBackupImport -> backupImportPicker.launch(arrayOf("*/*"))
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(extra.bg)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .testTag("main_search_list"),
        ) {
            // Top bar & security/theme actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = extra.brand,
                                ) {
                                    Text(
                                        text = "TNT",
                                        color = extra.brandInk,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        letterSpacing = 1.2.sp,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    )
                                }
                                Text(
                                    text = "Texniki baxış axtarışı",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = extra.ink,
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Excel faylını yükləyin, DQN və ya DQŞ-i yazın – baxışın bitmə tarixi və sahibin məlumatı çıxsın.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.muted,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = onOpenSecurityCenter,
                                shape = RoundedCornerShape(10.dp),
                                color = if (securityState.isInterventionUnlocked) extra.warnBg else extra.okBg,
                                border = BorderStroke(1.dp, extra.line),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_security_center"),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shield,
                                        contentDescription = "Təhlükəsizlik qalxanı",
                                        tint = if (securityState.isInterventionUnlocked) extra.warnText else extra.okText,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            val themeIcon = when (state.themeMode) {
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                                ThemeMode.SYSTEM -> Icons.Filled.Brightness6
                            }
                            Surface(
                                onClick = onCycleThemeMode,
                                shape = RoundedCornerShape(10.dp),
                                color = extra.surface,
                                border = BorderStroke(1.dp, extra.line),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_theme_toggle"),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = themeIcon,
                                        contentDescription = "Temanı dəyiş",
                                        tint = extra.ink,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        SmallActionChip(
                            label = if (securityState.isInterventionUnlocked) {
                                "Müdaxilə açıqdır"
                            } else {
                                "Müdaxilə kilidi"
                            },
                            icon = if (securityState.isInterventionUnlocked) Icons.Filled.LockOpen else Icons.Filled.Shield,
                            tintOverride = if (securityState.isInterventionUnlocked) extra.warnText else extra.okText,
                            bgOverride = if (securityState.isInterventionUnlocked) extra.warnBg else extra.okBg,
                            onClick = {
                                if (securityState.isInterventionUnlocked) {
                                    onLockInterventionMode()
                                } else {
                                    onRequestProtectedAction(PendingInterventionAction.UnlockModeOnly)
                                }
                            },
                            testTag = "btn_intervention_mode",
                        )
                        SmallActionChip(
                            label = "Ehtiyat nüsxə",
                            icon = Icons.Filled.Download,
                            onClick = {
                                onRequestProtectedAction(PendingInterventionAction.ExportBackup)
                            },
                            testTag = "btn_export_backup",
                        )
                        SmallActionChip(
                            label = "Nüsxədən yüklə",
                            icon = Icons.Filled.UploadFile,
                            onClick = {
                                onRequestProtectedAction(PendingInterventionAction.ImportBackup)
                            },
                            testTag = "btn_import_backup",
                        )
                        SmallActionChip(
                            label = "PIN dəyiş",
                            icon = Icons.Filled.Key,
                            onClick = {
                                onRequestProtectedAction(PendingInterventionAction.ChangePin)
                            },
                            testTag = "btn_change_pin",
                        )
                        SmallActionChip(
                            label = "Kilidlə",
                            icon = Icons.Filled.Lock,
                            onClick = onLockApp,
                            testTag = "btn_lock",
                        )
                    }
                }
            }

            // Excel File Loader Card
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extra.surface,
                    border = BorderStroke(1.dp, extra.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = {
                                    localMessage = null
                                    spreadsheetPicker.launch(arrayOf("*/*"))
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = extra.brand,
                                    contentColor = extra.brandInk,
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("excel_pick_btn"),
                            ) {
                                if (state.isLoadingFile) {
                                    CircularProgressIndicator(
                                        color = extra.brandInk,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.TableChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Excel yüklə", fontWeight = FontWeight.SemiBold)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                val infoText = if (state.rows.isNotEmpty()) {
                                    "Yüklənib: ${state.rows.size} nəqliyyat vasitəsi"
                                } else {
                                    "Fayl yüklənməyib"
                                }
                                Text(
                                    text = infoText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state.rows.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (state.rows.isNotEmpty()) extra.ink else extra.muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("loaded_count_text"),
                                )
                                if (state.sourceFileName.isNotBlank() && state.rows.isNotEmpty()) {
                                    Text(
                                        text = state.sourceFileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extra.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            if (state.raw.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        onRequestProtectedAction(PendingInterventionAction.ClearData)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, extra.line),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("excel_clear_btn"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Təmizlə",
                                        tint = extra.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Təmizlə", color = extra.muted)
                                }
                            }
                        }

                        val bannerMsg = state.fileError ?: localMessage ?: state.statusBanner
                        if (!bannerMsg.isNullOrBlank()) {
                            val isErr = state.fileError != null
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isErr) extra.badBg else extra.okBg,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = bannerMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isErr) extra.badText else extra.okText,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }

                        // Expandable column mapping override section
                        if (state.raw.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = onToggleColumnMapExpanded)
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                        .testTag("toggle_column_map_btn"),
                                ) {
                                    Icon(
                                        imageVector = if (state.mapPanelExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = extra.muted,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sütunlar düzgün seçilib?",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = extra.muted,
                                    )
                                }

                                AnimatedVisibility(visible = state.mapPanelExpanded) {
                                    val headers = state.raw.getOrElse(state.hdr) { emptyList() }
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 6.dp),
                                    ) {
                                        for (field in ColumnField.entries) {
                                            ColumnMappingDropdownRow(
                                                field = field,
                                                selectedCol = state.map.get(field),
                                                headers = headers,
                                                columnCount = state.columnCount,
                                                onSelectCol = { colIdx ->
                                                    onRequestProtectedAction(
                                                        PendingInterventionAction.UpdateColumn(field, colIdx)
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // AZ License Plate Search Bar & Owner Name Search Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(10.dp))
                            .background(extra.plateBg)
                            .border(3.dp, extra.plateLine, RoundedCornerShape(10.dp)),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(extra.plateLine)
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = "AZ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.8.sp,
                            )
                        }
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            if (state.plateQuery.isEmpty()) {
                                Text(
                                    text = "04BA-172",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        letterSpacing = 2.sp,
                                        color = Color(0xFF9AA396),
                                    ),
                                )
                            }
                            BasicTextField(
                                value = state.plateQuery,
                                onValueChange = { incoming -> onPlateQueryChange(incoming.uppercase()) },
                                singleLine = true,
                                cursorBrush = SolidColor(extra.plateLine),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    letterSpacing = 2.sp,
                                    color = Color(0xFF111111),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("plate_search_input"),
                            )
                        }
                        if (state.plateQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onPlateQueryChange("") },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Təmizlə",
                                    tint = Color(0xFF555555),
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.nameQuery,
                        onValueChange = onNameQueryChange,
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = extra.muted,
                            )
                        },
                        trailingIcon = {
                            if (state.nameQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onNameQueryChange("") },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Təmizlə",
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text("Ad, soyad və ya təşkilat üzrə axtar")
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = extra.surface,
                            unfocusedContainerColor = extra.surface,
                            focusedBorderColor = extra.brand,
                            unfocusedBorderColor = extra.line,
                            focusedTextColor = extra.ink,
                            unfocusedTextColor = extra.ink,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("name_search_input"),
                    )

                    if (state.rows.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StatusFilter.entries.forEach { filter ->
                                val count = when (filter) {
                                    StatusFilter.ALL -> state.summary.total
                                    StatusFilter.VALID -> state.summary.valid
                                    StatusFilter.SOON -> state.summary.soon
                                    StatusFilter.OVERDUE -> state.summary.overdue
                                    StatusFilter.NONE -> state.summary.none
                                }
                                FilterChip(
                                    selected = state.statusFilter == filter,
                                    onClick = { onSelectStatusFilter(filter) },
                                    label = {
                                        Text("${filter.labelAz} ($count)", style = MaterialTheme.typography.labelMedium)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = extra.brand,
                                        selectedLabelColor = extra.brandInk,
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("status_filter_${filter.name.lowercase()}"),
                                )
                            }
                        }
                    }
                }
            }

            // Results / Prompt area
            val outcome = state.outcome
            if (outcome.matches.isEmpty() && !outcome.promptMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = outcome.promptMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extra.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 12.dp)
                            .testTag("search_prompt_msg"),
                    )
                }
            } else {
                items(outcome.matches) { row ->
                    VehicleResultCard(
                        row = row,
                        onCallPhone = { rawPhone ->
                            val cleanPhone = rawPhone.filter { it.isDigit() || it == '+' }
                            if (cleanPhone.isNotEmpty()) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                                    )
                                }
                            }
                        },
                    )
                }
                if (outcome.extraHiddenCount > 0) {
                    item {
                        Text(
                            text = "Daha ${outcome.extraHiddenCount} nəticə var – nömrəni və ya adı dəqiqləşdirin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Fixed bottom author footer on main page
        Surface(
            color = extra.surface,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider(color = extra.line, thickness = 1.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = extra.brand,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Müəllif: Vaqif Qurbanov Əvəz",
                        style = MaterialTheme.typography.labelMedium,
                        color = extra.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("author_footer_text"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    tintOverride: Color? = null,
    bgOverride: Color? = null,
) {
    val extra = LocalTexnoColors.current
    val fg = tintOverride ?: extra.ink
    val bg = bgOverride ?: extra.surface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, extra.line),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ColumnMappingDropdownRow(
    field: ColumnField,
    selectedCol: Int,
    headers: List<String>,
    columnCount: Int,
    onSelectCol: (Int) -> Unit,
) {
    val extra = LocalTexnoColors.current
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = if (selectedCol < 0) {
        "— yoxdur —"
    } else {
        val hText = headers.getOrNull(selectedCol)?.trim().orEmpty()
        val colCode = TexnonezaretEngine.colName(selectedCol)
        if (hText.isNotEmpty()) "$colCode – $hText" else colCode
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = field.labelAz,
            style = MaterialTheme.typography.bodySmall,
            color = extra.muted,
        )
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = extra.bg,
                border = BorderStroke(1.dp, extra.line),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("col_map_${field.key}"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extra.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = extra.muted,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("— yoxdur —") },
                    onClick = {
                        expanded = false
                        onSelectCol(-1)
                    },
                )
                for (c in 0 until columnCount) {
                    val hText = headers.getOrNull(c)?.trim().orEmpty()
                    val label = if (hText.isNotEmpty()) {
                        "${TexnonezaretEngine.colName(c)} – $hText"
                    } else {
                        TexnonezaretEngine.colName(c)
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelectCol(c)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleResultCard(
    row: VehicleRow,
    onCallPhone: (String) -> Unit,
) {
    val extra = LocalTexnoColors.current
    val status = remember(row.inspectionUtcMillis) {
        TexnonezaretEngine.evaluateStatus(row.inspectionUtcMillis)
    }
    val (statusBg, statusFg) = when (status.kind) {
        InspectionStatusKind.VALID -> extra.okBg to extra.okText
        InspectionStatusKind.TODAY, InspectionStatusKind.SOON -> extra.warnBg to extra.warnText
        InspectionStatusKind.OVERDUE, InspectionStatusKind.NONE -> extra.badBg to extra.badText
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = extra.surface,
        border = BorderStroke(1.dp, extra.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vehicle_card_${row.normPlate.ifBlank { row.normChassis }}"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = row.plate.ifBlank { "DQN yoxdur" },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                letterSpacing = 1.6.sp,
                color = extra.ink,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = statusBg,
            ) {
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusFg,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow(label = "Baxış tarixi", value = status.inspectionDateText)
            DetailRow(label = "Bitmə tarixi", value = status.expiryDateText)
            DetailRow(label = "Mülkiyyətçi", value = row.owner.ifBlank { "—" })
            if (row.phone.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Text(
                        text = "Telefon",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                        modifier = Modifier.width(116.dp),
                    )
                    Surface(
                        onClick = { onCallPhone(row.phone) },
                        shape = RoundedCornerShape(8.dp),
                        color = extra.okBg.copy(alpha = 0.6f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = "Zəng et",
                                tint = extra.brand,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = row.phone,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = extra.brand,
                            )
                        }
                    }
                }
            } else {
                DetailRow(label = "Telefon", value = "—")
            }
            DetailRow(label = "DQŞ", value = row.chassis.ifBlank { "—" })
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    val extra = LocalTexnoColors.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = extra.muted,
            modifier = Modifier.width(116.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = extra.ink,
            modifier = Modifier.weight(1f),
        )
    }
}
