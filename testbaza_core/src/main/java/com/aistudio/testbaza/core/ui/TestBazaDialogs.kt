package com.aistudio.testbaza.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.ExamConfig
import com.aistudio.testbaza.core.domain.FileStatisticsSummary
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.SessionQuestionItem
import com.aistudio.testbaza.core.domain.TestBazaThemeMode
import com.aistudio.testbaza.core.ui.theme.LocalVipColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolsBottomSheet(
    uiState: TestBazaUiState,
    onSelectRule: (AnswerRule) -> Unit,
    onPickFile: () -> Unit,
    onOpenPasteDialog: () -> Unit,
    onOpenKeyEditor: () -> Unit,
    onExportHtml: () -> Unit,
    onSwitchBank: (Long) -> Unit,
    onDeleteBank: (QuizBank) -> Unit,
    onToggleTheme: () -> Unit,
    onResetProgressConfirmed: () -> Unit,
    onSetExamQuestionCount: (Int) -> Unit,
    onSetExamDurationMinutes: (Int) -> Unit,
    onStartLocalExam: () -> Unit,
    onOpenStatistics: () -> Unit,
    onClearStatisticsConfirmed: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearStatsConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("İrəliləyişi sıfırla?") },
            text = {
                Text("Bütün nəticə və səhv siyahısı sıfırlanacaq. Yüklənmiş suallar və seçilmiş doğru cavablar qalacaq.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetProgressConfirmed()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = vip.rose),
                    modifier = Modifier.testTag("confirm_reset_button"),
                ) {
                    Text("Sıfırla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Ləğv et")
                }
            },
        )
    }

    val activeBank = uiState.activeBank
    if (showClearStatsConfirm && activeBank != null) {
        AlertDialog(
            onDismissRequest = { showClearStatsConfirm = false },
            title = { Text("Statistika təmizlənsin?") },
            text = {
                Text("\"${activeBank.title}\" faylı üzrə hər suala sərf olunan vaxt, səhv sayı və imtahan tarixçəsi təmizlənəcək. Suallar və cavab açarı qalacaq.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearStatsConfirm = false
                        onClearStatisticsConfirmed(activeBank.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = vip.rose),
                    modifier = Modifier.testTag("tools_confirm_clear_stats_button"),
                ) {
                    Text("Təmizlə")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStatsConfirm = false }) {
                    Text("Ləğv et")
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = vip.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "VIP Alətlər və Tənzimləmələr",
                        style = MaterialTheme.typography.titleLarge,
                        color = vip.gold,
                    )
                    Text(
                        text = "İmtahan, statistika, fayl yükləmə və oflayn ixrac",
                        style = MaterialTheme.typography.bodyMedium,
                        color = vip.textSecondary,
                    )
                }
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tools_theme_toggle_button"),
                ) {
                    Icon(
                        imageVector = if (uiState.themeMode == TestBazaThemeMode.DARK) {
                            Icons.Default.LightMode
                        } else {
                            Icons.Default.DarkMode
                        },
                        contentDescription = "Görünüşü dəyiş",
                        tint = vip.gold,
                    )
                }
            }

            ExamSettingsSection(
                uiState = uiState,
                onSetQuestionCount = onSetExamQuestionCount,
                onSetDurationMinutes = onSetExamDurationMinutes,
                onStartLocalExam = {
                    onDismiss()
                    onStartLocalExam()
                },
            )

            StatisticsSettingsSection(
                stats = uiState.activeBankStatistics,
                hasBanks = uiState.banks.isNotEmpty(),
                onOpenStatistics = {
                    onDismiss()
                    onOpenStatistics()
                },
                onRequestClearStatistics = { showClearStatsConfirm = true },
            )

            HorizontalDivider(color = vip.border)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Yeni fayl yüklənəndə ilkin doğru cavab qaydası:",
                    style = MaterialTheme.typography.labelMedium,
                    color = vip.textSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnswerRule.entries.forEach { rule ->
                        FilterChip(
                            selected = uiState.importRule == rule,
                            onClick = { onSelectRule(rule) },
                            label = { Text(rule.label) },
                            modifier = Modifier.testTag("rule_chip_${rule.name.lowercase()}"),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onPickFile()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("tools_pick_file_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vip.emerald,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Word və ya PDF yüklə",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Word / PDF")
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenPasteDialog()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("tools_paste_text_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, vip.borderStrong),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Mətn yapışdır",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Mətn Yapışdır")
                }
            }

            ToolRowAction(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                title = "Cavab Seçimi və Redaktə (${uiState.keyedCount}/${uiState.totalCount})",
                subtitle = "Sualları, variantları və doğru cavabları redaktə et",
                accentColor = vip.gold,
                testTag = "tools_key_editor_button",
                onClick = {
                    onDismiss()
                    onOpenKeyEditor()
                },
            )

            ToolRowAction(
                icon = Icons.Default.Download,
                title = "HTML Saxla",
                subtitle = "Bu bazanı oflayn işləyən tək HTML faylı kimi ixrac et",
                accentColor = vip.emerald,
                testTag = "tools_export_html_button",
                onClick = {
                    onDismiss()
                    onExportHtml()
                },
            )

            ToolRowAction(
                icon = Icons.Default.RestartAlt,
                title = "İrəliləyişi Sıfırla",
                subtitle = "Suallar və cavab açarı qalır, nəticə tarixçəsi təmizlənir",
                accentColor = vip.rose,
                testTag = "tools_reset_progress_button",
                onClick = { showResetConfirm = true },
            )

            if (uiState.banks.isNotEmpty()) {
                HorizontalDivider(color = vip.border)
                Text(
                    text = "Saxlanılan Test Bazaları (${uiState.banks.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = vip.textPrimary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.banks.forEach { bank ->
                        val isActive = bank.id == uiState.activeBank?.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSwitchBank(bank.id)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) vip.emeraldSoft else vip.surfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isActive) vip.emeraldBorder else vip.border,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bank.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = vip.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${bank.questionCount} sual",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isActive) vip.emerald else vip.textSecondary,
                                    )
                                }
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Aktiv baza",
                                        tint = vip.emerald,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteBank(bank) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Bazanı sil",
                                        tint = vip.rose,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamSettingsSection(
    uiState: TestBazaUiState,
    onSetQuestionCount: (Int) -> Unit,
    onSetDurationMinutes: (Int) -> Unit,
    onStartLocalExam: () -> Unit,
) {
    val vip = LocalVipColors.current
    val bankMax = uiState.totalCount.takeIf { it > 0 }?.let { minOf(it, ExamConfig.MAX_QUESTIONS) }
        ?: ExamConfig.MAX_QUESTIONS
    val effectiveQuestions = uiState.examConfig.questionCount.coerceIn(1, bankMax)
    val durationMin = uiState.examConfig.durationMinutes

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = vip.surfaceElevated,
        border = BorderStroke(1.dp, vip.goldBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "İmtahan Bölməsi",
                    tint = vip.gold,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "İmtahan Bölməsi",
                    style = MaterialTheme.typography.titleMedium,
                    color = vip.gold,
                )
            }

            // Question count setting
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Düşəcək sual sayı:",
                        style = MaterialTheme.typography.labelMedium,
                        color = vip.textPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { onSetQuestionCount((effectiveQuestions - 5).coerceAtLeast(1)) },
                            modifier = Modifier.size(48.dp).testTag("exam_questions_minus_button"),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Azalt", tint = vip.textSecondary)
                        }
                        Text(
                            text = "$effectiveQuestions sual",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = vip.gold,
                        )
                        IconButton(
                            onClick = { onSetQuestionCount((effectiveQuestions + 5).coerceAtMost(ExamConfig.MAX_QUESTIONS)) },
                            modifier = Modifier.size(48.dp).testTag("exam_questions_plus_button"),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Artır", tint = vip.emerald)
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(10, 20, 30, 50, 100).forEach { preset ->
                        FilterChip(
                            selected = effectiveQuestions == preset,
                            onClick = { onSetQuestionCount(preset) },
                            label = { Text("$preset") },
                            modifier = Modifier.testTag("exam_count_chip_$preset"),
                        )
                    }
                }
            }

            // Duration setting
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "İmtahan vaxtı:",
                        style = MaterialTheme.typography.labelMedium,
                        color = vip.textPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { onSetDurationMinutes((durationMin - 5).coerceAtLeast(1)) },
                            modifier = Modifier.size(48.dp).testTag("exam_minutes_minus_button"),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Vaxtı azalt", tint = vip.textSecondary)
                        }
                        Text(
                            text = "$durationMin dəq",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = vip.emerald,
                        )
                        IconButton(
                            onClick = { onSetDurationMinutes((durationMin + 5).coerceAtMost(ExamConfig.MAX_DURATION_MINUTES)) },
                            modifier = Modifier.size(48.dp).testTag("exam_minutes_plus_button"),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Vaxtı artır", tint = vip.emerald)
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(10, 15, 30, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = durationMin == mins,
                            onClick = { onSetDurationMinutes(mins) },
                            label = { Text("$mins dəq") },
                            modifier = Modifier.testTag("exam_time_chip_$mins"),
                        )
                    }
                }
            }

            Button(
                onClick = onStartLocalExam,
                enabled = uiState.totalCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("exam_start_local_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = vip.emerald,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("İmtahana Başla")
            }
        }
    }
}

@Composable
private fun StatisticsSettingsSection(
    stats: FileStatisticsSummary?,
    hasBanks: Boolean,
    onOpenStatistics: () -> Unit,
    onRequestClearStatistics: () -> Unit,
) {
    val vip = LocalVipColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = vip.surfaceElevated,
        border = BorderStroke(1.dp, vip.emeraldBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Statistika Bölməsi",
                        tint = vip.emerald,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Statistika Bölməsi",
                            style = MaterialTheme.typography.titleMedium,
                            color = vip.emerald,
                        )
                        Text(
                            text = stats?.bankTitle ?: "Fayl seçilməyib",
                            style = MaterialTheme.typography.bodySmall,
                            color = vip.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (stats != null && stats.totalAttempts > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = vip.emeraldSoft,
                        border = BorderStroke(1.dp, vip.emeraldBorder),
                    ) {
                        Text(
                            text = "${stats.accuracyPercent}% dəqiqlik",
                            style = MaterialTheme.typography.labelSmall,
                            color = vip.emerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (stats != null) {
                Text(
                    text = "Orta sual vaxtı: ${stats.avgSecondsPerTimedQuestion} san • Səhv sual: ${stats.weakCount} • Ümumi vaxt: ${QuizEngine.formatDurationHumanAz(stats.totalTimeSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = vip.textPrimary,
                )
            } else {
                Text(
                    text = "Yüklənən fayl üzrə hər sualın vaxtı, səhvlər və imtahan hazırlığı göstəriciləri burada görünəcək.",
                    style = MaterialTheme.typography.bodySmall,
                    color = vip.textSecondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenStatistics,
                    enabled = hasBanks,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("tools_open_statistics_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vip.gold,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Statistikaya Bax", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onRequestClearStatistics,
                    enabled = stats != null && (stats.totalAttempts > 0 || stats.totalTimeSeconds > 0L || stats.weakCount > 0),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("tools_clear_statistics_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, vip.roseBorder),
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Statistikanı təmizlə",
                        tint = vip.rose,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Təmizlə", color = vip.rose)
                }
            }
        }
    }
}

@Composable
private fun ToolRowAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color,
    testTag: String,
    onClick: () -> Unit,
) {
    val vip = LocalVipColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = vip.surfaceElevated,
        border = BorderStroke(1.dp, vip.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = vip.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = vip.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasteTextDialog(
    selectedRule: AnswerRule,
    onSelectRule: (AnswerRule) -> Unit,
    onImportText: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    var title by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.borderStrong),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mətndən Test Yarat",
                        style = MaterialTheme.typography.titleLarge,
                        color = vip.textPrimary,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Bağla",
                            tint = vip.textSecondary,
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Test bazasının adı (istəyə bağlı)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_title_input"),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnswerRule.entries.forEach { rule ->
                        FilterChip(
                            selected = selectedRule == rule,
                            onClick = { onSelectRule(rule) },
                            label = { Text(rule.label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("1. Sual?\nA) Variant\nB) Variant...") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("paste_body_input"),
                )

                Button(
                    onClick = {
                        onImportText(title, rawText)
                        onDismiss()
                    },
                    enabled = rawText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("paste_submit_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vip.emerald,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Analiz et və Yüklə")
                }
            }
        }
    }
}

@Composable
fun QuestionGridDialog(
    items: List<SessionQuestionItem>,
    currentIndex: Int,
    onJumpToIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.goldBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Suallar Xəritəsi (${items.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = vip.gold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Bağla",
                            tint = vip.textSecondary,
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    itemsIndexed(items) { idx, item ->
                        val isCurrent = idx == currentIndex
                        val bg = when {
                            isCurrent -> vip.emerald
                            item.isCorrect == true -> vip.emeraldSoft
                            item.isCorrect == false -> vip.roseSoft
                            item.question.masteryStatus == QuestionMasteryStatus.MASTERED -> vip.emeraldSoft.copy(alpha = 0.45f)
                            item.question.masteryStatus == QuestionMasteryStatus.WEAK -> vip.roseSoft.copy(alpha = 0.45f)
                            item.question.isExam -> vip.goldSoft
                            else -> vip.surfaceElevated
                        }
                        val border = when {
                            isCurrent -> vip.emerald
                            item.isCorrect == true -> vip.emeraldBorder
                            item.isCorrect == false -> vip.roseBorder
                            item.question.isExam -> vip.goldBorder
                            else -> vip.border
                        }
                        val txtColor = when {
                            isCurrent -> MaterialTheme.colorScheme.onPrimary
                            item.isCorrect == true -> vip.emerald
                            item.isCorrect == false -> vip.rose
                            item.question.isExam -> vip.gold
                            else -> vip.textPrimary
                        }
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onJumpToIndex(idx)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = bg,
                            border = BorderStroke(1.dp, border),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = item.question.questionNumber.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = txtColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
