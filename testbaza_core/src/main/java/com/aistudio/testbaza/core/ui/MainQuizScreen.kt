package com.aistudio.testbaza.core.ui

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Text
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
    onOpenFilePicker: () -> Unit,
) {
    val vip = LocalVipColors.current

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
                uiState.mode != StudyMode.FLASHCARD &&
                !uiState.isSessionFinished &&
                uiState.currentSessionItem != null
            ) {
                QuizBottomDock(
                    canGoPrev = uiState.currentIndex > 0,
                    canUseJoker = !uiState.currentSessionItem!!.isAnswered,
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
                StudyModePillBar(
                    uiState = uiState,
                    onSwitchMode = onSwitchMode,
                )

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
                                text = if (uiState.isImporting) "Fayl analiz edilir..." else "Test bazası hazırlanır...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = vip.textSecondary,
                            )
                        }
                    }
                } else if (uiState.rawQuestions.isEmpty()) {
                    EmptyBankCard(onPickFile = onOpenFilePicker)
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
    val hasUnconfirmed = uiState.totalCount > 0 && uiState.keyedCount < uiState.totalCount

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
                        text = uiState.activeBank?.title ?: "TestBaza",
                        style = MaterialTheme.typography.titleMedium,
                        color = vip.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (uiState.totalCount > 0) {
                            "${uiState.totalCount} sual • " +
                                if (uiState.isShuffled) "Qarışıq" else "Sıralı"
                        } else {
                            "Oflayn Test Bazası"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = vip.gold,
                        maxLines = 1,
                    )
                }

                IconButton(
                    onClick = onOpenGrid,
                    enabled = uiState.sessionItems.isNotEmpty(),
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
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("header_tools_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Alətlər və fayl yükləmə",
                            tint = vip.gold,
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (totalActive > 0) "Sual ${activeIdx + 1} / $totalActive" else "0 / 0",
                    style = MaterialTheme.typography.labelSmall,
                    color = vip.gold,
                    fontWeight = FontWeight.Bold,
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = activeChunkLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = vip.emerald,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = chunkMenuOpen,
                        onDismissRequest = { chunkMenuOpen = false },
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    enabled = canGoPrev,
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
                    Text("50:50", color = vip.gold)
                }

                OutlinedButton(
                    onClick = onMarkMastered,
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

                Button(
                    onClick = onNext,
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
                    Text(if (isLastQuestion) "Bitir" else "Növbəti")
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

@Composable
private fun EmptyBankCard(onPickFile: () -> Unit) {
    val vip = LocalVipColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = vip.surface,
            border = BorderStroke(1.dp, vip.borderStrong),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Test Bazası Yükləyin",
                    style = MaterialTheme.typography.titleLarge,
                    color = vip.textPrimary,
                )
                Text(
                    text = "Word (.docx), PDF (.pdf) və ya TXT faylı seçərək oflayn test yaradın.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vip.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onPickFile,
                    modifier = Modifier.heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = vip.emerald),
                ) {
                    Text("Fayl Seç")
                }
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
