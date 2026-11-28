package com.aistudio.testbaza.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.testbaza.core.domain.ExamAttemptHistoryItem
import com.aistudio.testbaza.core.domain.FileStatisticsSummary
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.ShareResultPayload
import com.aistudio.testbaza.core.ui.theme.LocalVipColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StatsTab(val label: String) {
    SUMMARY("Ümumi"),
    ERRORS("Səhvlər"),
    TIMINGS("Sual Vaxtları"),
    HISTORY("Tarixçə"),
}

@Composable
fun ShareResultDialog(
    payload: ShareResultPayload,
    onCopySuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    val context = LocalContext.current
    var includeDetails by remember { mutableStateOf(true) }

    val formattedText = remember(payload, includeDetails) {
        QuizEngine.formatResultShareText(payload, includeDetails = includeDetails)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.2.dp, vip.goldBorder),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 540.dp)
                .fillMaxWidth(),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nəticəni Paylaş",
                            style = MaterialTheme.typography.titleLarge,
                            color = vip.gold,
                        )
                        Text(
                            text = payload.title.ifBlank { "Testbaza" },
                            style = MaterialTheme.typography.bodySmall,
                            color = vip.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("share_result_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Bağla",
                            tint = vip.textSecondary,
                        )
                    }
                }

                Text(
                    text = "Paylaşım formatını seçin:",
                    style = MaterialTheme.typography.labelLarge,
                    color = vip.textPrimary,
                )

                ShareModeOptionCard(
                    title = "Statistik məlumatlar və səhvlərlə birgə",
                    subtitle = "Nəticə faizi, orta/ən sürətli/ən yavaş sual, hər sualın vaxtı və səhv edilən sualların tam izahı",
                    selected = includeDetails,
                    testTag = "share_mode_detailed_card",
                    onClick = { includeDetails = true },
                )

                ShareModeOptionCard(
                    title = "Sadəcə nəticə olaraq paylaş",
                    subtitle = "Yalnız test adı, düzgün/səhv/boş sayı, bal faizi və ümumi müddət",
                    selected = !includeDetails,
                    testTag = "share_mode_summary_card",
                    onClick = { includeDetails = false },
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = vip.surfaceElevated,
                    border = BorderStroke(1.dp, vip.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                    ) {
                        Text(
                            text = formattedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = vip.textPrimary,
                            modifier = Modifier.testTag("share_result_preview_text"),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            copyTextToClipboard(context, formattedText)
                            onCopySuccess()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("share_result_copy_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, vip.borderStrong),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Nəticəni kopyala",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Kopyala")
                    }

                    Button(
                        onClick = { shareTextViaIntent(context, payload.title, formattedText) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("share_result_intent_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vip.emerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Paylaş",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Paylaş")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareModeOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val vip = LocalVipColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) vip.emeraldSoft else vip.surfaceElevated,
        border = BorderStroke(
            width = if (selected) 1.4.dp else 1.dp,
            color = if (selected) vip.emeraldBorder else vip.border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = title,
                tint = if (selected) vip.emerald else vip.textSecondary.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) vip.emerald else vip.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = vip.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileStatisticsDialog(
    banks: List<QuizBank>,
    selectedBankId: Long?,
    stats: FileStatisticsSummary?,
    history: List<ExamAttemptHistoryItem>,
    onSelectBank: (Long) -> Unit,
    onClearStatistics: (Long) -> Unit,
    onPracticeWeak: (Long) -> Unit,
    onShareHistoryRecord: (ShareResultPayload) -> Unit,
    onDismiss: () -> Unit,
) {
    val vip = LocalVipColors.current
    var activeTab by remember { mutableStateOf(StatsTab.SUMMARY) }
    var sortTimingsBySlowest by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val hasAnyStats = stats != null && (
        stats.totalAttempts > 0 ||
            stats.totalTimeSeconds > 0L ||
            stats.totalWrongAttempts > 0 ||
            stats.answeredQuestionsCount > 0 ||
            history.isNotEmpty()
        )

    if (showClearConfirm && stats != null) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Statistika təmizlənsin?") },
            text = {
                Text("\"${stats.bankTitle}\" faylına aid bütün sual vaxtları, səhvlər, mənimsəmə statusları və imtahan tarixçəsi silinəcək. Suallar və doğru cavablar saxlanacaq.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearStatistics(stats.bankId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = vip.rose),
                    modifier = Modifier.testTag("confirm_clear_stats_button"),
                ) {
                    Text("Təmizlə")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Ləğv et")
                }
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.2.dp, vip.goldBorder),
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = 690.dp),
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
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(vip.goldSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Statistika",
                                tint = vip.gold,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Statistika Bölməsi",
                                style = MaterialTheme.typography.titleLarge,
                                color = vip.gold,
                            )
                            Text(
                                text = stats?.bankTitle ?: "Yüklənmiş fayl üzrə analiz",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stats_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Bağla",
                            tint = vip.textSecondary,
                        )
                    }
                }

                if (banks.isEmpty() || stats == null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = vip.surfaceElevated,
                        border = BorderStroke(1.dp, vip.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Statistika görmək üçün əvvəlcə Word, PDF və ya TXT test faylı yükləyin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = vip.textSecondary,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                } else {
                    if (banks.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(banks, key = { it.id }) { bank ->
                                val selected = bank.id == selectedBankId
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectBank(bank.id) },
                                    label = {
                                        Text(
                                            text = "${bank.title} (${bank.questionCount})",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier.testTag("stats_bank_chip_${bank.id}"),
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        StatsTab.entries.forEach { tab ->
                            val tabLabel = when (tab) {
                                StatsTab.ERRORS -> "Səhvlər (${stats.mostWrongQuestions.size})"
                                StatsTab.HISTORY -> "Tarixçə (${history.size})"
                                else -> tab.label
                            }
                            FilterChip(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                label = { Text(tabLabel) },
                                modifier = Modifier.testTag("stats_tab_${tab.name.lowercase()}"),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                    ) {
                        when (activeTab) {
                            StatsTab.SUMMARY -> SummaryStatsTabContent(stats = stats)
                            StatsTab.ERRORS -> ErrorsStatsTabContent(
                                stats = stats,
                                onPracticeWeak = {
                                    onDismiss()
                                    onPracticeWeak(stats.bankId)
                                },
                            )
                            StatsTab.TIMINGS -> TimingsStatsTabContent(
                                stats = stats,
                                sortBySlowest = sortTimingsBySlowest,
                                onToggleSort = { sortTimingsBySlowest = !sortTimingsBySlowest },
                            )
                            StatsTab.HISTORY -> HistoryStatsTabContent(
                                history = history,
                                onShareRecord = { record ->
                                    onShareHistoryRecord(
                                        ShareResultPayload(
                                            title = record.bankTitle,
                                            summary = record.toEvaluationSummary(),
                                            totalElapsedSeconds = record.totalElapsedSeconds,
                                            durationLimitMinutes = record.durationLimitMinutes.takeIf { record.isTimedExam && it > 0 },
                                            timedOut = record.timedOut,
                                        )
                                    )
                                },
                            )
                        }
                    }

                    HorizontalDivider(color = vip.border)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            enabled = hasAnyStats,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("stats_clear_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (hasAnyStats) vip.roseBorder else vip.border,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Statistikanı təmizlə",
                                tint = if (hasAnyStats) vip.rose else vip.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "Təmizlə",
                                color = if (hasAnyStats) vip.rose else vip.textSecondary,
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = vip.emerald,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Bağla")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsTabContent(stats: FileStatisticsSummary) {
    val vip = LocalVipColors.current
    val coverageFraction = if (stats.totalQuestions > 0) {
        stats.answeredQuestionsCount.toFloat() / stats.totalQuestions.toFloat()
    } else 0f

    val examReadinessTip = when {
        stats.totalAttempts == 0 ->
            "Sualları işlədikcə hər suala sərf olunan vaxt, dəqiqlik faizi və səhv etdiyiniz mövzular burada avtomatik hesablanacaq."
        stats.weakCount > 0 && stats.avgSecondsPerTimedQuestion > 45L ->
            "İmtahandan əvvəl ${stats.weakCount} səhv sualı təkrarlayın. Orta cavab vaxtınız (${stats.avgSecondsPerTimedQuestion} san) bir qədər yüksəkdir — sürətli sınaq rejimi faydalı olar."
        stats.weakCount > 0 ->
            "${stats.weakCount} sualda səhviniz qeydə alınıb. 'Səhvlər' bölməsindən onları ayrıca məşq edərək nəticənizi yüksəldə bilərsiniz."
        stats.accuracyPercent >= 85 && stats.answeredQuestionsCount >= stats.totalQuestions / 2 ->
            "İmtahana hazırlığınız əladır (${stats.accuracyPercent}% dəqiqlik). Vaxt idarəetməsini qorumaq üçün qarışıq sınaq imtahanı işləyin."
        else ->
            "Bazanı tam əhatə etmək üçün qalan ${stats.newCount} yeni sualı da tamamlayın."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsKpiCard(
                title = "Dəqiqlik",
                value = "${stats.accuracyPercent}%",
                subtitle = "${stats.totalCorrectAttempts} düzgün • ${stats.totalWrongAttempts} səhv",
                accent = vip.emerald,
                modifier = Modifier.weight(1f),
            )
            StatsKpiCard(
                title = "Ümumi Vaxt",
                value = QuizEngine.formatDurationHumanAz(stats.totalTimeSeconds),
                subtitle = "Orta: ${stats.avgSecondsPerTimedQuestion} san/sual",
                accent = vip.gold,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsKpiCard(
                title = "Mənimsənilib",
                value = "${stats.masteredCount}/${stats.totalQuestions}",
                subtitle = "${stats.newCount} yeni sual",
                accent = vip.emerald,
                modifier = Modifier.weight(1f),
            )
            StatsKpiCard(
                title = "Səhv Bazası",
                value = "${stats.weakCount} sual",
                subtitle = "Cəmi ${stats.totalAttempts} cavab",
                accent = if (stats.weakCount > 0) vip.rose else vip.emerald,
                modifier = Modifier.weight(1f),
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = vip.surfaceElevated,
            border = BorderStroke(1.dp, vip.goldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = vip.gold,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "İmtahana Hazırlıq Analizi",
                            style = MaterialTheme.typography.titleSmall,
                            color = vip.gold,
                        )
                    }
                    Text(
                        text = "${stats.answeredQuestionsCount}/${stats.totalQuestions} sual",
                        style = MaterialTheme.typography.labelMedium,
                        color = vip.textSecondary,
                    )
                }

                LinearProgressIndicator(
                    progress = { coverageFraction },
                    color = vip.emerald,
                    trackColor = vip.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                )

                if (stats.estimated50QuestionSeconds > 0L) {
                    val estCount = minOf(50, maxOf(1, stats.totalQuestions))
                    Text(
                        text = "$estCount suallıq imtahan üçün təxmini sürətiniz: ${QuizEngine.formatDurationHumanAz(stats.estimated50QuestionSeconds)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = vip.emerald,
                    )
                }

                Text(
                    text = examReadinessTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = vip.textSecondary,
                )
            }
        }

        if (stats.slowestQuestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = vip.surfaceElevated,
                border = BorderStroke(1.dp, vip.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Ən Çox Vaxt Aparan Suallar",
                        style = MaterialTheme.typography.titleSmall,
                        color = vip.textPrimary,
                    )
                    stats.slowestQuestions.forEach { q ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${q.questionNumber}. ${q.questionText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            Text(
                                text = "${q.averageTimeSeconds} san",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = vip.gold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val vip = LocalVipColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = vip.surfaceElevated,
        border = BorderStroke(1.dp, vip.border),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = vip.textSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = vip.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorsStatsTabContent(
    stats: FileStatisticsSummary,
    onPracticeWeak: () -> Unit,
) {
    val vip = LocalVipColors.current
    if (stats.mostWrongQuestions.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = vip.emeraldSoft,
            border = BorderStroke(1.dp, vip.emeraldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Bu faylda hələ səhv cavab qeydə alınmayıb.",
                style = MaterialTheme.typography.bodyMedium,
                color = vip.emerald,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (stats.weakCount > 0) {
                Button(
                    onClick = onPracticeWeak,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("stats_practice_weak_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vip.rose,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Səhvləri Məşq Et (${stats.weakCount} sual)")
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(stats.mostWrongQuestions, key = { it.rowId }) { q ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = vip.surfaceElevated,
                        border = BorderStroke(1.dp, vip.roseBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = vip.rose,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Sual #${q.questionNumber} • ${q.wrongCount}x səhv",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = vip.rose,
                                    )
                                }
                                if (q.averageTimeSeconds > 0L) {
                                    Text(
                                        text = "Orta: ${q.averageTimeSeconds} san",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = vip.gold,
                                    )
                                }
                            }
                            Text(
                                text = q.questionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textPrimary,
                            )
                            Text(
                                text = "Doğru cavab: ${q.correctText}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = vip.emerald,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingsStatsTabContent(
    stats: FileStatisticsSummary,
    sortBySlowest: Boolean,
    onToggleSort: () -> Unit,
) {
    val vip = LocalVipColors.current
    val itemsList = remember(stats.timedQuestions, sortBySlowest) {
        if (sortBySlowest) {
            stats.timedQuestions.sortedByDescending { it.averageTimeSeconds }
        } else {
            stats.timedQuestions.sortedBy { it.questionNumber }
        }
    }

    if (itemsList.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = vip.surfaceElevated,
            border = BorderStroke(1.dp, vip.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Hələ bu faylda cavablandırılmış sual vaxtı qeydə alınmayıb.",
                style = MaterialTheme.typography.bodyMedium,
                color = vip.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${itemsList.size} sual üzrə vaxt statistikası",
                    style = MaterialTheme.typography.labelMedium,
                    color = vip.textSecondary,
                )
                FilterChip(
                    selected = sortBySlowest,
                    onClick = onToggleSort,
                    label = { Text(if (sortBySlowest) "Ən yavaşdan" else "Sual sırası") },
                    modifier = Modifier.testTag("stats_timings_sort_chip"),
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(itemsList, key = { it.rowId }) { q ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = vip.surfaceElevated,
                        border = BorderStroke(1.dp, vip.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Sual #${q.questionNumber}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = vip.gold,
                                )
                                Text(
                                    text = "Orta: ${q.averageTimeSeconds} san • Son: ${q.lastTimeSeconds} san",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = vip.emerald,
                                )
                            }
                            Text(
                                text = q.questionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Cəmi vaxt: ${QuizEngine.formatDurationHumanAz(q.totalTimeSeconds)} • Cəhd: ${q.attemptCount} • Səhv: ${q.wrongCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = vip.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatsTabContent(
    history: List<ExamAttemptHistoryItem>,
    onShareRecord: (ExamAttemptHistoryItem) -> Unit,
) {
    val vip = LocalVipColors.current
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    if (history.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = vip.surfaceElevated,
            border = BorderStroke(1.dp, vip.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Hələ tamamlanmış imtahan və ya test sessiyası tarixçəsi yoxdur.",
                style = MaterialTheme.typography.bodyMedium,
                color = vip.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(history, key = { it.id }) { item ->
                val pct = if (item.totalQuestions > 0) (item.correctCount * 100) / item.totalQuestions else 0
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = vip.surfaceElevated,
                    border = BorderStroke(1.dp, vip.borderStrong),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = if (item.isTimedExam) Icons.Default.Timer else Icons.Default.History,
                                    contentDescription = null,
                                    tint = if (item.isTimedExam) vip.gold else vip.emerald,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (item.isTimedExam) "İmtahan ($pct%)" else "Məşq ($pct%)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = vip.textPrimary,
                                )
                                Text(
                                    text = dateFmt.format(Date(item.createdAtEpochMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = vip.textSecondary,
                                )
                            }
                            Text(
                                text = "Düzgün: ${item.correctCount} • Səhv: ${item.wrongCount} • Boş: ${item.unansweredCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.emerald,
                            )
                            Text(
                                text = "Vaxt: ${QuizEngine.formatDurationHumanAz(item.totalElapsedSeconds)}" +
                                    if (item.timedOut) " (Vaxt bitdi)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = vip.gold,
                            )
                        }

                        OutlinedButton(
                            onClick = { onShareRecord(item) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("history_share_button_${item.id}"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, vip.goldBorder),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Nəticəni paylaş",
                                tint = vip.gold,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("Paylaş", color = vip.gold)
                        }
                    }
                }
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Testbaza Nəticə", text))
}

private fun shareTextViaIntent(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Testbaza — ${title.ifBlank { "Nəticə" }}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Nəticəni paylaş"))
}
