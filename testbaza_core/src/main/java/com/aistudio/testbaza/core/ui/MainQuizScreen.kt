package com.aistudio.testbaza.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.SessionQuestionItem
import com.aistudio.testbaza.core.domain.StudyMode
import com.aistudio.testbaza.core.ui.theme.LocalVipColors

@Composable
fun MainQuizScreen(
    uiState: TestBazaUiState,
    snackbarHostState: SnackbarHostState,
    onSwitchMode: (StudyMode) -> Unit,
    onSelectChunk: (Int?) -> Unit,
    onToggleShuffle: () -> Unit,
    onSelectOption: (Int) -> Unit,
    onUseJoker5050: () -> Unit,
    onMarkMastered: () -> Unit,
    onToggleStar: () -> Unit,
    onPrevQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onRestartSession: () -> Unit,
    onReturnToFullQuiz: () -> Unit,
    onFlipFlashcard: () -> Unit,
    onPrevFlashcard: () -> Unit,
    onNextFlashcard: () -> Unit,
    onOpenSingleKeyDialog: () -> Unit,
    onOpenGridDialog: () -> Unit,
    onOpenToolsSheet: () -> Unit,
    onOpenWordPicker: () -> Unit,
    onOpenPdfPicker: () -> Unit,
    onOpenTxtPicker: () -> Unit,
    onOpenPasteDialog: () -> Unit,
    onSelectImportRule: (AnswerRule) -> Unit,
    onFinishExam: () -> Unit,
    onExitExam: () -> Unit,
    onToggleSharedExamConsent: (Boolean) -> Unit,
    onRetryUploadAttempt: () -> Unit,
    onOpenJoinExam: () -> Unit,
) {
    val vip = LocalVipColors.current
    var showExitExamConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isExamActive && !uiState.isSessionFinished) {
        showExitExamConfirm = true
    }

    if (showExitExamConfirm) {
        AlertDialog(
            onDismissRequest = { showExitExamConfirm = false },
            title = { Text("İmtahandan çıxılsın?") },
            text = {
                Text("Aktiv imtahan yarımçıq dayandırılacaq və taymer sıfırlanacaq.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitExamConfirm = false
                        onExitExam()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = vip.rose),
                    modifier = Modifier.testTag("confirm_exit_exam_button"),
                ) {
                    Text("İmtahanı Dayandır")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitExamConfirm = false }) {
                    Text("Davam et")
                }
            },
        )
    }

    Scaffold(
        containerColor = vip.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (uiState.isStatusError) vip.rose else vip.surfaceElevated,
                    contentColor = vip.textPrimary,
                )
            }
        },
        topBar = {
            VipTopHeader(
                uiState = uiState,
                onOpenGrid = onOpenGridDialog,
                onOpenTools = onOpenToolsSheet,
            )
        },
        bottomBar = {
            if (!uiState.isLoading &&
                !uiState.isImporting &&
                (uiState.rawQuestions.isNotEmpty() || uiState.isExamActive) &&
                uiState.mode != StudyMode.FLASHCARD &&
                !uiState.isSessionFinished &&
                uiState.currentSessionItem != null
            ) {
                QuizBottomDock(
                    canGoPrev = uiState.currentIndex > 0,
                    canUseJoker = !uiState.currentSessionItem!!.isAnswered,
                    showMasteredButton = !uiState.isExamActive,
                    isLastQuestion = uiState.currentIndex >= uiState.sessionItems.lastIndex,
                    onPrev = onPrevQuestion,
                    onJoker = onUseJoker5050,
                    onMarkMastered = onMarkMastered,
                    onNext = onNextQuestion,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                if (uiState.isExamActive && !uiState.isSessionFinished) {
                    ActiveExamHudBar(
                        uiState = uiState,
                        onFinishExam = onFinishExam,
                        onRequestExitExam = { showExitExamConfirm = true },
                    )
                } else if (!uiState.isExamActive && uiState.rawQuestions.isNotEmpty()) {
                    StudyModePillBar(
                        uiState = uiState,
                        onSwitchMode = onSwitchMode,
                    )
                }

                if (uiState.isLoading || uiState.isImporting) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(color = vip.gold)
                            Text(
                                text = if (uiState.isImporting) "Fayl analiz edilir..." else "Testbazaya hazırlanır...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = vip.textSecondary,
                            )
                        }
                    }
                } else if (uiState.isExamActive) {
                    if (uiState.isSessionFinished) {
                        ExamCompleteVipCard(
                            uiState = uiState,
                            onToggleSharedExamConsent = onToggleSharedExamConsent,
                            onRetryUploadAttempt = onRetryUploadAttempt,
                            onRestart = onRestartSession,
                            onExitExam = onExitExam,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (uiState.currentSessionItem != null) {
                        QuizActiveBody(
                            item = uiState.currentSessionItem!!,
                            showStudyActions = false,
                            onSelectOption = onSelectOption,
                            onToggleStar = onToggleStar,
                            onEditKey = onOpenSingleKeyDialog,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        EmptyFilteredModeCard(
                            title = "İmtahanda sual tapılmadı",
                            onBackToAll = onExitExam,
                        )
                    }
                } else if (uiState.rawQuestions.isEmpty()) {
                    InitialImportHomeScreen(
                        selectedRule = uiState.importRule,
                        isCloudAvailable = uiState.isCloudAvailable,
                        onSelectRule = onSelectImportRule,
                        onPickWord = onOpenWordPicker,
                        onPickPdf = onOpenPdfPicker,
                        onPickTxt = onOpenTxtPicker,
                        onOpenPaste = onOpenPasteDialog,
                        onOpenJoinExam = onOpenJoinExam,
                    )
                } else {
                    TelemetryAndFilterRow(
                        uiState = uiState,
                        onSelectChunk = onSelectChunk,
                        onToggleShuffle = onToggleShuffle,
                    )

                    when {
                        uiState.mode == StudyMode.FLASHCARD -> {
                            val cardItem = uiState.currentFlashcardItem
                            if (cardItem != null) {
                                FlashcardSection(
                                    item = cardItem,
                                    currentIndex = uiState.flashcardIndex,
                                    totalCount = uiState.sessionItems.size,
                                    isFlipped = uiState.isFlashcardFlipped,
                                    onFlip = onFlipFlashcard,
                                    onPrevious = onPrevFlashcard,
                                    onNext = onNextFlashcard,
                                    onToggleStar = onToggleStar,
                                    modifier = Modifier
                                        .weight(1f)
                                        .navigationBarsPadding()
                                        .padding(bottom = 12.dp),
                                )
                            } else {
                                EmptyFilteredModeCard(
                                    title = "Kart tapılmadı",
                                    onBackToAll = onReturnToFullQuiz,
                                )
                            }
                        }

                        uiState.isSessionFinished -> {
                            SessionCompleteVipCard(
                                correctCount = uiState.correctCount,
                                incorrectCount = uiState.incorrectCount,
                                onRestart = onRestartSession,
                                onReturnAll = onReturnToFullQuiz,
                            )
                        }

                        uiState.currentSessionItem != null -> {
                            QuizActiveBody(
                                item = uiState.currentSessionItem!!,
                                showStudyActions = true,
                                onSelectOption = onSelectOption,
                                onToggleStar = onToggleStar,
                                onEditKey = onOpenSingleKeyDialog,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        else -> {
                            EmptyFilteredModeCard(
                                title = "Bu bölmədə sual yoxdur",
                                onBackToAll = onReturnToFullQuiz,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VipTopHeader(
    uiState: TestBazaUiState,
    onOpenGrid: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val vip = LocalVipColors.current
    val hasUnconfirmed = !uiState.isExamActive && uiState.totalCount > 0 && uiState.keyedCount < uiState.totalCount

    Surface(
        color = vip.surface,
        border = BorderStroke(0.5.dp, vip.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(listOf(vip.goldSoft, vip.emeraldSoft))
                        )
                        .padding(1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "TestBaza VIP",
                        tint = vip.gold,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.isExamActive) {
                            uiState.activeExam.title.ifBlank { "Sınaq İmtahanı" }
                        } else {
                            uiState.activeBank?.title ?: "Testbaza"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = vip.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            uiState.isExamActive -> {
                                val codePrefix = uiState.activeExam.examCode?.let { "Kod: $it • " }.orEmpty()
                                "$codePrefix${uiState.sessionItems.size} sual • ${uiState.activeExam.durationMinutes} dəq"
                            }
                            uiState.totalCount > 0 -> {
                                "${uiState.totalCount} sual • " +
                                    if (uiState.isShuffled) "Qarışıq" else "Sıralı"
                            }
                            else -> "Oflayn Test Bazası"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = vip.gold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = onOpenGrid,
                    enabled = uiState.sessionItems.isNotEmpty() && !uiState.isSessionFinished,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("header_grid_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Suallar xəritəsi",
                        tint = vip.textPrimary,
                    )
                }

                Box {
                    IconButton(
                        onClick = onOpenTools,
                        enabled = !uiState.isExamActive || uiState.isSessionFinished,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("header_tools_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Alətlər və fayl yükləmə",
                            tint = if (!uiState.isExamActive || uiState.isSessionFinished) vip.gold else vip.textSecondary.copy(alpha = 0.4f),
                        )
                    }
                    if (hasUnconfirmed) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(vip.gold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveExamHudBar(
    uiState: TestBazaUiState,
    onFinishExam: () -> Unit,
    onRequestExitExam: () -> Unit,
) {
    val vip = LocalVipColors.current
    val exam = uiState.activeExam
    val remaining = exam.remainingSeconds
    val timerColor = when {
        remaining <= 30 -> vip.rose
        remaining <= 120 -> vip.gold
        else -> vip.emerald
    }
    val timerBg = when {
        remaining <= 30 -> vip.roseSoft
        remaining <= 120 -> vip.goldSoft
        else -> vip.emeraldSoft
    }
    val totalActive = uiState.sessionItems.size
    val progressFraction by animateFloatAsState(
        targetValue = if (totalActive > 0) (uiState.currentIndex + 1).toFloat() / totalActive.toFloat() else 0f,
        label = "exam_progress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.goldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = timerBg,
                    border = BorderStroke(1.dp, timerColor.copy(alpha = 0.6f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Qalan vaxt",
                            tint = timerColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = QuizEngine.formatTimerMmSs(remaining),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = timerColor,
                            modifier = Modifier.testTag("exam_countdown_text"),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sual ${uiState.currentIndex + 1}/$totalActive",
                        style = MaterialTheme.typography.labelLarge,
                        color = vip.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = "Bu sual: ${exam.currentQuestionElapsedSeconds} san",
                        style = MaterialTheme.typography.labelSmall,
                        color = vip.gold,
                        maxLines = 1,
                        modifier = Modifier.testTag("exam_question_timer_text"),
                    )
                }

                OutlinedButton(
                    onClick = onFinishExam,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .testTag("exam_hud_finish_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, vip.emeraldBorder),
                ) {
                    Text(
                        text = "Bitir",
                        color = vip.emerald,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                IconButton(
                    onClick = onRequestExitExam,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("exam_hud_exit_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "İmtahandan çıx",
                        tint = vip.rose,
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { progressFraction },
            color = vip.gold,
            trackColor = vip.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
        )
    }
}

@Composable
private fun StudyModePillBar(
    uiState: TestBazaUiState,
    onSwitchMode: (StudyMode) -> Unit,
) {
    val vip = LocalVipColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = vip.surface,
        border = BorderStroke(1.dp, vip.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StudyMode.entries.forEach { mode ->
                val selected = uiState.mode == mode
                val label = when (mode) {
                    StudyMode.QUIZ -> "Hamısı"
                    StudyMode.FLASHCARD -> "Kartlar"
                    StudyMode.EXAM -> "Seç (${uiState.examCount})"
                    StudyMode.WEAK -> "Səhv (${uiState.weakCount})"
                }
                val icon = when (mode) {
                    StudyMode.QUIZ -> Icons.Default.Quiz
                    StudyMode.FLASHCARD -> Icons.Default.Layers
                    StudyMode.EXAM -> Icons.Default.Star
                    StudyMode.WEAK -> Icons.Default.LocalFireDepartment
                }
                val pillBg by animateColorAsState(
                    targetValue = when {
                        !selected -> vip.surface
                        mode == StudyMode.EXAM -> vip.goldSoft
                        mode == StudyMode.WEAK -> vip.roseSoft
                        else -> vip.emeraldSoft
                    },
                    label = "mode_pill_bg",
                )
                val pillTint = when {
                    !selected -> vip.textSecondary
                    mode == StudyMode.EXAM -> vip.gold
                    mode == StudyMode.WEAK -> vip.rose
                    else -> vip.emerald
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSwitchMode(mode) }
                        .testTag("mode_tab_${mode.name.lowercase()}"),
                    shape = RoundedCornerShape(12.dp),
                    color = pillBg,
                    border = if (selected) BorderStroke(1.dp, pillTint.copy(alpha = 0.55f)) else null,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = pillTint,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) vip.textPrimary else vip.textSecondary,
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
private fun TelemetryAndFilterRow(
    uiState: TestBazaUiState,
    onSelectChunk: (Int?) -> Unit,
    onToggleShuffle: () -> Unit,
) {
    val vip = LocalVipColors.current
    var chunkMenuOpen by remember { mutableStateOf(false) }

    val activeIdx = if (uiState.mode == StudyMode.FLASHCARD) uiState.flashcardIndex else uiState.currentIndex
    val totalActive = uiState.sessionItems.size
    val progressFraction by animateFloatAsState(
        targetValue = if (totalActive > 0) (activeIdx + 1).toFloat() / totalActive.toFloat() else 0f,
        label = "quiz_progress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (totalActive > 0) "Sual ${activeIdx + 1}/$totalActive" else "0/0",
                    style = MaterialTheme.typography.labelSmall,
                    color = vip.gold,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
                StatDot(icon = Icons.Default.LocalFireDepartment, value = uiState.streakCount, color = vip.gold)
                StatDot(icon = Icons.Default.CheckCircle, value = uiState.correctCount, color = vip.emerald)
                StatDot(icon = Icons.Default.Cancel, value = uiState.incorrectCount, color = vip.rose)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    val activeChunkLabel = uiState.chunkOptions
                        .firstOrNull { it.index == uiState.selectedChunkIndex }
                        ?.label
                        ?.substringBefore(" (")
                        ?: "Bütün baza"

                    Surface(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { chunkMenuOpen = true }
                            .testTag("chunk_selector_button"),
                        shape = RoundedCornerShape(10.dp),
                        color = vip.surface,
                        border = BorderStroke(1.dp, vip.border),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = activeChunkLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = vip.emerald,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = chunkMenuOpen,
                        onDismissRequest = { chunkMenuOpen = false },
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        uiState.chunkOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = {
                                    chunkMenuOpen = false
                                    onSelectChunk(opt.index)
                                },
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleShuffle)
                        .testTag("shuffle_toggle_button"),
                    shape = RoundedCornerShape(10.dp),
                    color = if (uiState.isShuffled) vip.emeraldSoft else vip.surface,
                    border = BorderStroke(1.dp, if (uiState.isShuffled) vip.emeraldBorder else vip.border),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Qarışıq rejim",
                            tint = if (uiState.isShuffled) vip.emerald else vip.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (uiState.isShuffled) "Qarışıq" else "Sıralı",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.isShuffled) vip.emerald else vip.textSecondary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { progressFraction },
            color = vip.emerald,
            trackColor = vip.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
        )
    }
}

@Composable
private fun StatDot(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun QuizActiveBody(
    item: SessionQuestionItem,
    showStudyActions: Boolean,
    onSelectOption: (Int) -> Unit,
    onToggleStar: () -> Unit,
    onEditKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vip = LocalVipColors.current
    val q = item.question

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.borderStrong),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = vip.goldSoft,
                            border = BorderStroke(1.dp, vip.goldBorder),
                        ) {
                            Text(
                                text = "SUAL #${q.questionNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = vip.gold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        if (showStudyActions) {
                            val (mLabel, mBg, mColor) = when (q.masteryStatus) {
                                QuestionMasteryStatus.NEW -> Triple("Yeni", vip.surfaceElevated, vip.textSecondary)
                                QuestionMasteryStatus.MASTERED -> Triple("✓ Mənimsənilib", vip.emeraldSoft, vip.emerald)
                                QuestionMasteryStatus.WEAK -> Triple("Təkrar (${q.wrongCount}x)", vip.roseSoft, vip.rose)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = mBg,
                            ) {
                                Text(
                                    text = mLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    if (showStudyActions) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onEditKey,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("question_edit_key_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Doğru cavabı seç",
                                    tint = vip.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = onToggleStar,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("question_star_button"),
                            ) {
                                Icon(
                                    imageVector = if (q.isExam) Icons.Default.Star else Icons.Outlined.StarOutline,
                                    contentDescription = if (q.isExam) "Seçilmişdən çıxar" else "Seçilmişə əlavə et",
                                    tint = if (q.isExam) vip.gold else vip.textSecondary,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = q.questionText,
                    style = MaterialTheme.typography.titleLarge,
                    color = vip.textPrimary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item.shuffledOptions.forEachIndexed { idx, optText ->
                VipOptionButton(
                    index = idx,
                    text = optText,
                    isDisabledByJoker = idx in item.disabledOptions,
                    isAnswered = item.isAnswered,
                    isCorrectOption = idx == item.correctIdx,
                    isSelectedWrong = item.isAnswered && item.selectedIdx == idx && item.isCorrect == false,
                    onClick = { onSelectOption(idx) },
                )
            }
        }
    }
}

@Composable
private fun VipOptionButton(
    index: Int,
    text: String,
    isDisabledByJoker: Boolean,
    isAnswered: Boolean,
    isCorrectOption: Boolean,
    isSelectedWrong: Boolean,
    onClick: () -> Unit,
) {
    val vip = LocalVipColors.current
    val shakeOffset = remember(index, isSelectedWrong) { Animatable(0f) }

    LaunchedEffect(isSelectedWrong) {
        if (isSelectedWrong) {
            val offsets = listOf(-14f, 14f, -10f, 10f, -5f, 0f)
            for (x in offsets) {
                shakeOffset.animateTo(x, animationSpec = tween(45))
            }
        }
    }

    val containerColor by animateColorAsState(
        targetValue = when {
            isAnswered && isCorrectOption -> vip.emeraldSoft
            isSelectedWrong -> vip.roseSoft
            else -> vip.surface
        },
        label = "opt_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isAnswered && isCorrectOption -> vip.emeraldBorder
            isSelectedWrong -> vip.roseBorder
            else -> vip.borderStrong
        },
        label = "opt_border",
    )
    val badgeBg = when {
        isAnswered && isCorrectOption -> vip.emerald
        isSelectedWrong -> vip.rose
        else -> vip.surfaceElevated
    }
    val badgeText = when {
        isAnswered && isCorrectOption -> MaterialTheme.colorScheme.onPrimary
        isSelectedWrong -> MaterialTheme.colorScheme.onError
        else -> vip.gold
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .graphicsLayer { translationX = shakeOffset.value }
            .alpha(if (isDisabledByJoker) 0.25f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isAnswered && !isDisabledByJoker, onClick = onClick)
            .testTag("quiz_option_$index"),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = QuizEngine.optionPrefix(index),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeText,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = vip.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuizBottomDock(
    canGoPrev: Boolean,
    canUseJoker: Boolean,
    showMasteredButton: Boolean,
    isLastQuestion: Boolean,
    onPrev: () -> Unit,
    onJoker: () -> Unit,
    onMarkMastered: () -> Unit,
    onNext: () -> Unit,
) {
    val vip = LocalVipColors.current
    Surface(
        color = vip.surface,
        border = BorderStroke(0.5.dp, vip.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    enabled = canGoPrev,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("bottom_prev_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, vip.borderStrong),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Əvvəlki sual",
                        modifier = Modifier.size(18.dp),
                    )
                }

                OutlinedButton(
                    onClick = onJoker,
                    enabled = canUseJoker,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("bottom_joker_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, vip.goldBorder),
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "50:50 jokeri",
                        tint = vip.gold,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "50:50",
                        color = vip.gold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                if (showMasteredButton) {
                    OutlinedButton(
                        onClick = onMarkMastered,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("bottom_mastered_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, vip.emeraldBorder),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Mənimsədim",
                            tint = vip.emerald,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Button(
                    onClick = onNext,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("bottom_next_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vip.emerald,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = if (isLastQuestion) "Bitir" else "Növbəti",
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (isLastQuestion) "Testi bitir" else "Növbəti sual",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCompleteVipCard(
    correctCount: Int,
    incorrectCount: Int,
    onRestart: () -> Unit,
    onReturnAll: () -> Unit,
) {
    val vip = LocalVipColors.current
    val total = correctCount + incorrectCount
    val pct = if (total > 0) (correctCount * 100) / total else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = vip.surface,
            border = BorderStroke(1.2.dp, vip.goldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(vip.goldSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Sessiya tamamlandı",
                        tint = vip.gold,
                        modifier = Modifier.size(34.dp),
                    )
                }

                Text(
                    text = "Sessiya Tamamlandı",
                    style = MaterialTheme.typography.headlineSmall,
                    color = vip.textPrimary,
                )

                Text(
                    text = "Dəqiqlik: $pct% ($correctCount düzgün • $incorrectCount səhv)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = vip.gold,
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onReturnAll,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Bütün Baza")
                    }
                    Button(
                        onClick = onRestart,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("session_restart_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vip.emerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Yenidən başla",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Yenidən")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamCompleteVipCard(
    uiState: TestBazaUiState,
    onToggleSharedExamConsent: (Boolean) -> Unit,
    onRetryUploadAttempt: () -> Unit,
    onRestart: () -> Unit,
    onExitExam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vip = LocalVipColors.current
    val exam = uiState.activeExam
    val summary = exam.evaluationSummary
    val total = summary?.totalQuestions ?: uiState.sessionItems.size
    val correct = summary?.correctCount ?: uiState.correctCount
    val wrong = summary?.wrongCount ?: uiState.incorrectCount
    val unanswered = summary?.unansweredCount ?: maxOf(0, total - correct - wrong)
    val pct = if (total > 0) (correct * 100) / total else 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.2.dp, vip.goldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(vip.goldSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "İmtahan tamamlandı",
                        tint = vip.gold,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Text(
                    text = if (exam.timedOut) "Vaxt Bitdi — İmtahan Yekunlaşdı" else "İmtahan Tamamlandı",
                    style = MaterialTheme.typography.headlineSmall,
                    color = vip.textPrimary,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Nəticə: $pct%  ($correct düzgün • $wrong səhv • $unanswered boş)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = vip.emerald,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Ümumi sərf olunan vaxt: ${QuizEngine.formatDurationHumanAz(exam.totalElapsedSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vip.gold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (exam.isSharedExam && exam.sharedExam != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (exam.shareConsentGranted) vip.emeraldSoft else vip.surface,
                border = BorderStroke(
                    1.dp,
                    if (exam.shareConsentGranted) vip.emeraldBorder else vip.borderStrong,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exam_result_consent_card"),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Nəticə paylaşma icazəsi",
                            tint = if (exam.shareConsentGranted) vip.emerald else vip.gold,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nəticəni paylaşan şəxsə göstər",
                                style = MaterialTheme.typography.titleMedium,
                                color = vip.textPrimary,
                            )
                            Text(
                                text = "Paylaşan (${exam.sharedExam.ownerName}) yalnız siz icazə verdikdə nəticənizi, səhvlərinizi və hər sual üzrə vaxtınızı görə bilər.",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textSecondary,
                            )
                        }
                        Switch(
                            checked = exam.shareConsentGranted,
                            onCheckedChange = onToggleSharedExamConsent,
                            enabled = !exam.attemptUploading,
                            modifier = Modifier.testTag("exam_result_consent_switch"),
                        )
                    }

                    when {
                        exam.attemptUploading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(color = vip.emerald, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Nəticə göndərilir...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vip.emerald,
                                )
                            }
                        }
                        !exam.uploadErrorMessage.isNullOrBlank() -> {
                            Text(
                                text = exam.uploadErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.rose,
                            )
                            OutlinedButton(
                                onClick = onRetryUploadAttempt,
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Yenidən göndər")
                            }
                        }
                        exam.shareConsentGranted && exam.attemptUploaded -> {
                            Text(
                                text = "✓ Nəticəniz, səhv suallar və sual vaxtları icazənizlə paylaşan şəxsə göndərildi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.emerald,
                            )
                        }
                        else -> {
                            Text(
                                text = "İcazə bağlıdır — nəticəniz yalnız bu cihazda saxlanılır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = vip.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        if (summary != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = vip.surface,
                border = BorderStroke(1.dp, vip.borderStrong),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (summary.wrongDetails.isEmpty()) {
                            "Səhv Edilən Suallar (0)"
                        } else {
                            "Səhv Edilən Suallar (${summary.wrongDetails.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (summary.wrongDetails.isEmpty()) vip.emerald else vip.rose,
                    )

                    if (summary.wrongDetails.isEmpty()) {
                        Text(
                            text = "Təbriklər! Heç bir sualda səhv etmədiniz.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = vip.textSecondary,
                        )
                    } else {
                        summary.wrongDetails.forEach { detail ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = vip.roseSoft.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, vip.roseBorder),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "Sual #${detail.questionNumber} (${QuizEngine.formatDurationHumanAz(detail.timeSpentSeconds)}): ${detail.questionText}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = vip.textPrimary,
                                    )
                                    Text(
                                        text = "Sizin cavab: ${detail.selectedAnswer}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = vip.rose,
                                    )
                                    Text(
                                        text = "Doğru cavab: ${detail.correctAnswer}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = vip.emerald,
                                    )
                                }
                            }
                        }
                    }

                    if (summary.questionTimings.isNotEmpty()) {
                        HorizontalDivider(color = vip.border)
                        Text(
                            text = "Hər Sual Üzrə Sərf Olunan Vaxt:",
                            style = MaterialTheme.typography.labelLarge,
                            color = vip.gold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            summary.questionTimings.forEach { t ->
                                val chipBg = when (t.status) {
                                    "CORRECT" -> vip.emeraldSoft
                                    "WRONG" -> vip.roseSoft
                                    else -> vip.surfaceElevated
                                }
                                val chipFg = when (t.status) {
                                    "CORRECT" -> vip.emerald
                                    "WRONG" -> vip.rose
                                    else -> vip.textSecondary
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipFg.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        text = "#${t.questionNumber} • ${t.timeSpentSeconds} san",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = chipFg,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onExitExam,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("exam_exit_home_button"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Əsas Səhifə")
            }
            Button(
                onClick = onRestart,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("exam_restart_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = vip.emerald,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Yenidən başla",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Yenidən")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InitialImportHomeScreen(
    selectedRule: AnswerRule,
    isCloudAvailable: Boolean,
    onSelectRule: (AnswerRule) -> Unit,
    onPickWord: () -> Unit,
    onPickPdf: () -> Unit,
    onPickTxt: () -> Unit,
    onOpenPaste: () -> Unit,
    onOpenJoinExam: () -> Unit,
) {
    val vip = LocalVipColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.goldBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(vip.goldSoft, vip.emeraldSoft))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = "Sual faylı yüklə",
                        tint = vip.gold,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Text(
                    text = "Suallarınızı Yükləyin",
                    style = MaterialTheme.typography.headlineSmall,
                    color = vip.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Word (.docx) və ya PDF sənədinizi seçin — suallar avtomatik analiz olunub testə hazır vəziyyətə gətiriləcək.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vip.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 126.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onPickWord)
                    .testTag("home_import_word_button"),
                shape = RoundedCornerShape(20.dp),
                color = vip.emeraldSoft,
                border = BorderStroke(1.2.dp, vip.emeraldBorder),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(vip.emerald),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Word faylı yüklə",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = "Word (.docx)",
                        style = MaterialTheme.typography.titleMedium,
                        color = vip.textPrimary,
                    )
                    Text(
                        text = "Word sənədindən sual əlavə et",
                        style = MaterialTheme.typography.bodySmall,
                        color = vip.textSecondary,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 126.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onPickPdf)
                    .testTag("home_import_pdf_button"),
                shape = RoundedCornerShape(20.dp),
                color = vip.goldSoft,
                border = BorderStroke(1.2.dp, vip.goldBorder),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(vip.gold),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF faylı yüklə",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = "PDF (.pdf)",
                        style = MaterialTheme.typography.titleMedium,
                        color = vip.textPrimary,
                    )
                    Text(
                        text = "Mətn əsaslı PDF faylını seç",
                        style = MaterialTheme.typography.bodySmall,
                        color = vip.textSecondary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onPickTxt,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("home_import_txt_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, vip.borderStrong),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TextSnippet,
                    contentDescription = "TXT faylı",
                    tint = vip.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("TXT Fayl", maxLines = 1, softWrap = false)
            }

            OutlinedButton(
                onClick = onOpenPaste,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("home_paste_text_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, vip.borderStrong),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Mətn yapışdır",
                    tint = vip.gold,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Mətn Yapışdır", maxLines = 1, softWrap = false)
            }
        }

        if (isCloudAvailable) {
            OutlinedButton(
                onClick = onOpenJoinExam,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("home_join_shared_exam_button"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, vip.goldBorder),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = "Paylaşılan imtahana qoşul",
                    tint = vip.gold,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Paylaşılan İmtahana Kodu ilə Qoşul",
                    color = vip.gold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Doğru cavabı tanıma qaydası:",
                    style = MaterialTheme.typography.labelLarge,
                    color = vip.gold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnswerRule.entries.forEach { rule ->
                        FilterChip(
                            selected = selectedRule == rule,
                            onClick = { onSelectRule(rule) },
                            label = { Text(rule.label) },
                            modifier = Modifier.testTag("home_rule_chip_${rule.name.lowercase()}"),
                        )
                    }
                }
                Text(
                    text = "Format nümunəsi:  1. Sual mətni?   A) Variant 1   B) Variant 2 ...\nSənədin sonunda cavab açarı (1. A  2. B...) varsa avtomatik oxunur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = vip.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyFilteredModeCard(
    title: String,
    onBackToAll: () -> Unit,
) {
    val vip = LocalVipColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = vip.textPrimary,
                )
                Button(
                    onClick = onBackToAll,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = vip.emerald),
                ) {
                    Text("Bütün Suallara Qayıt")
                }
            }
        }
    }
}
