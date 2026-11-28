package com.aistudio.testbaza.core.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.testbaza.core.domain.QuizQuestion
import com.aistudio.testbaza.core.domain.ShareResultPayload
import com.aistudio.testbaza.core.ui.theme.TestBazaTheme

@Composable
fun TestBazaRoot(
    viewModel: TestBazaViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showToolsSheet by remember { mutableStateOf(false) }
    var showKeyEditor by remember { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<QuizQuestion?>(null) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showStatisticsDialog by remember { mutableStateOf(false) }
    var shareResultPayload by remember { mutableStateOf<ShareResultPayload?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFileUri(uri) {
                showKeyEditor = true
            }
        }
    }

    val createHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportHtmlToUri(uri)
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissStatusMessage()
    }

    val launchDocumentPicker = {
        openDocLauncher.launch(arrayOf("*/*"))
    }

    val launchPdfPicker = {
        openDocLauncher.launch(arrayOf("application/pdf", "application/octet-stream", "*/*"))
    }

    val launchWordPicker = {
        openDocLauncher.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "application/octet-stream",
                "*/*",
            )
        )
    }

    TestBazaTheme(themeMode = uiState.themeMode) {
        MainQuizScreen(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onSwitchMode = viewModel::switchMode,
            onSelectChunk = viewModel::selectChunk,
            onToggleShuffle = viewModel::toggleShuffle,
            onSelectOption = viewModel::selectOption,
            onUseJoker5050 = viewModel::useJoker5050,
            onMarkMastered = viewModel::markCurrentMastered,
            onToggleStar = viewModel::toggleCurrentStar,
            onPrevQuestion = viewModel::prevQuestion,
            onNextQuestion = viewModel::nextQuestion,
            onRestartSession = viewModel::restartSession,
            onReturnToFullQuiz = viewModel::returnToFullQuiz,
            onFlipFlashcard = viewModel::flipFlashcard,
            onPrevFlashcard = viewModel::prevFlashcard,
            onNextFlashcard = viewModel::nextFlashcard,
            onOpenSingleKeyDialog = {
                editingQuestion = uiState.currentSessionItem?.question
            },
            onOpenGridDialog = { showGridDialog = true },
            onOpenToolsSheet = { showToolsSheet = true },
            onOpenWordPicker = launchWordPicker,
            onOpenPdfPicker = launchPdfPicker,
            onOpenTxtPicker = launchDocumentPicker,
            onOpenPasteDialog = { showPasteDialog = true },
            onSelectImportRule = viewModel::setImportRule,
            onFinishExam = { viewModel.finishActiveExam(timedOut = false) },
            onExitExam = viewModel::exitExamMode,
            onOpenShareResult = { payload -> shareResultPayload = payload },
        )

        if (showToolsSheet) {
            ToolsBottomSheet(
                uiState = uiState,
                onSelectRule = viewModel::setImportRule,
                onPickFile = launchDocumentPicker,
                onOpenPasteDialog = { showPasteDialog = true },
                onOpenKeyEditor = { showKeyEditor = true },
                onExportHtml = {
                    createHtmlLauncher.launch(viewModel.suggestedExportFilename())
                },
                onSwitchBank = viewModel::switchBank,
                onDeleteBank = viewModel::deleteBank,
                onToggleTheme = viewModel::toggleTheme,
                onResetProgressConfirmed = viewModel::resetProgress,
                onSetExamQuestionCount = viewModel::setExamQuestionCount,
                onSetExamDurationMinutes = viewModel::setExamDurationMinutes,
                onStartLocalExam = viewModel::startLocalTimedExam,
                onOpenStatistics = {
                    uiState.activeBank?.id?.let(viewModel::selectStatisticsBank)
                    showStatisticsDialog = true
                },
                onClearStatisticsConfirmed = viewModel::clearBankStatistics,
                onDismiss = { showToolsSheet = false },
            )
        }

        if (showStatisticsDialog) {
            FileStatisticsDialog(
                banks = uiState.banks,
                selectedBankId = uiState.selectedStatsBankId,
                stats = uiState.selectedBankStatistics,
                history = uiState.selectedBankExamHistory,
                onSelectBank = viewModel::selectStatisticsBank,
                onClearStatistics = viewModel::clearBankStatistics,
                onPracticeWeak = viewModel::practiceWeakQuestionsForBank,
                onShareHistoryRecord = { payload -> shareResultPayload = payload },
                onDismiss = { showStatisticsDialog = false },
            )
        }

        shareResultPayload?.let { payload ->
            ShareResultDialog(
                payload = payload,
                onCopySuccess = {
                    viewModel.showStatus("Nəticə panoya kopyalandı.", isError = false)
                },
                onDismiss = { shareResultPayload = null },
            )
        }

        if (showKeyEditor && uiState.rawQuestions.isNotEmpty() && !uiState.isExamActive) {
            KeyEditorModalDialog(
                questions = uiState.rawQuestions,
                keyedCount = uiState.keyedCount,
                onSelectAnswer = { q, opt ->
                    viewModel.updateSingleAnswerKey(q, opt, reshuffleNow = false)
                },
                onEditQuestionClick = { q ->
                    editingQuestion = q
                },
                onSelectAllFirst = viewModel::selectAllFirstOptions,
                onStartShuffledQuiz = viewModel::startQuizFromKeyEditor,
                onDismiss = { showKeyEditor = false },
            )
        }

        val targetEditQuestion = editingQuestion
        if (targetEditQuestion != null && !uiState.isExamActive) {
            EditQuestionDialog(
                question = targetEditQuestion,
                onSave = { newText, newOpts, newCorrect, reshuffle ->
                    viewModel.updateQuestionContent(
                        question = targetEditQuestion,
                        newQuestionText = newText,
                        newOptions = newOpts,
                        newCorrectText = newCorrect,
                        reshuffleNow = reshuffle,
                    )
                },
                onDeleteQuestion = {
                    viewModel.deleteSingleQuestion(targetEditQuestion)
                },
                onDismiss = { editingQuestion = null },
            )
        }

        if (showGridDialog && uiState.sessionItems.isNotEmpty() && !uiState.isSessionFinished) {
            QuestionGridDialog(
                items = uiState.sessionItems,
                currentIndex = uiState.currentIndex,
                onJumpToIndex = viewModel::jumpToQuestionIndex,
                onDismiss = { showGridDialog = false },
            )
        }

        if (showPasteDialog) {
            PasteTextDialog(
                selectedRule = uiState.importRule,
                onSelectRule = viewModel::setImportRule,
                onImportText = { title, body ->
                    viewModel.importRawText(title, body) {
                        showKeyEditor = true
                    }
                },
                onDismiss = { showPasteDialog = false },
            )
        }
    }
}
