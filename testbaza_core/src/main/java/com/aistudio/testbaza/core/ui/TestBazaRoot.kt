package com.aistudio.testbaza.core.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudio.testbaza.core.data.remote.TestBazaAuthHelper
import com.aistudio.testbaza.core.domain.ExamConfig
import com.aistudio.testbaza.core.ui.theme.TestBazaTheme

private enum class PendingCloudAction {
    NONE,
    SHARE_EXAM,
    JOIN_EXAM,
    MY_SHARED_EXAMS,
}

@Composable
fun TestBazaRoot(
    firestoreDatabaseId: String = "",
    defaultWebClientId: String = "",
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TestBazaViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                TestBazaViewModel(app, firestoreDatabaseId)
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showToolsSheet by remember { mutableStateOf(false) }
    var showKeyEditor by remember { mutableStateOf(false) }
    var showSingleKeyDialog by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }

    var showAuthDialog by remember { mutableStateOf(false) }
    var isAuthBusy by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingCloudAction by remember { mutableStateOf(PendingCloudAction.NONE) }

    var showPublishExamDialog by remember { mutableStateOf(false) }
    var showJoinExamDialog by remember { mutableStateOf(false) }
    var showMySharedExamsDialog by remember { mutableStateOf(false) }

    var autoSignInAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isCloudAvailable, defaultWebClientId) {
        if (!autoSignInAttempted && uiState.isCloudAvailable && defaultWebClientId.isNotBlank() && !uiState.isSignedIn) {
            autoSignInAttempted = true
            TestBazaAuthHelper.attemptAutoSignIn(
                context = context,
                webClientId = defaultWebClientId,
                credentialManager = credentialManager,
                scope = coroutineScope,
                onSignedIn = {},
                onUnauthenticated = {},
            )
        }
    }

    fun executeCloudAction(action: PendingCloudAction) {
        when (action) {
            PendingCloudAction.SHARE_EXAM -> {
                if (uiState.totalCount == 0) {
                    viewModel.showStatus("Paylaşmaq üçün əvvəlcə sual bazası yükləyin.", isError = true)
                } else {
                    viewModel.clearLatestPublishedExam()
                    showPublishExamDialog = true
                }
            }
            PendingCloudAction.JOIN_EXAM -> {
                viewModel.clearFetchedPreviewExam()
                showJoinExamDialog = true
            }
            PendingCloudAction.MY_SHARED_EXAMS -> {
                viewModel.inspectSharedExamAttempts(null)
                showMySharedExamsDialog = true
            }
            PendingCloudAction.NONE -> Unit
        }
    }

    fun requireAuthThen(action: PendingCloudAction) {
        if (!uiState.isCloudAvailable) {
            viewModel.showStatus("Bulud paylaşımı bu quruluşda aktiv deyil.", isError = true)
            return
        }
        if (uiState.isSignedIn) {
            executeCloudAction(action)
        } else {
            pendingCloudAction = action
            authErrorMessage = null
            showAuthDialog = true
        }
    }

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

    // Use "*/*" so Android OEM file pickers (Samsung/Xiaomi/Telegram/WhatsApp) never grey out .docx/.pdf/.txt files;
    // QuizRepository inspects magic bytes + extension + MIME type and gives clear messages for unsupported files.
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
            onOpenSingleKeyDialog = { showSingleKeyDialog = true },
            onOpenGridDialog = { showGridDialog = true },
            onOpenToolsSheet = { showToolsSheet = true },
            onOpenWordPicker = launchWordPicker,
            onOpenPdfPicker = launchPdfPicker,
            onOpenTxtPicker = launchDocumentPicker,
            onOpenPasteDialog = { showPasteDialog = true },
            onSelectImportRule = viewModel::setImportRule,
            onFinishExam = { viewModel.finishActiveExam(timedOut = false) },
            onExitExam = viewModel::exitExamMode,
            onToggleSharedExamConsent = viewModel::setSharedExamConsentAndUpload,
            onRetryUploadAttempt = viewModel::uploadConsentedAttemptIfAllowed,
            onOpenJoinExam = { requireAuthThen(PendingCloudAction.JOIN_EXAM) },
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
                onOpenShareExam = { requireAuthThen(PendingCloudAction.SHARE_EXAM) },
                onOpenJoinExam = { requireAuthThen(PendingCloudAction.JOIN_EXAM) },
                onOpenMySharedExams = { requireAuthThen(PendingCloudAction.MY_SHARED_EXAMS) },
                onOpenGoogleSignIn = {
                    pendingCloudAction = PendingCloudAction.NONE
                    authErrorMessage = null
                    showAuthDialog = true
                },
                onSignOut = {
                    TestBazaAuthHelper.signOutUser(credentialManager, coroutineScope) {
                        viewModel.showStatus("Google hesabından çıxış edildi.", isError = false)
                    }
                },
                onDismiss = { showToolsSheet = false },
            )
        }

        if (showAuthDialog) {
            GoogleAuthPromptDialog(
                isBusy = isAuthBusy,
                errorMessage = authErrorMessage,
                onGoogleSignInClick = {
                    isAuthBusy = true
                    authErrorMessage = null
                    TestBazaAuthHelper.signInWithGoogle(
                        context = context,
                        webClientId = defaultWebClientId,
                        credentialManager = credentialManager,
                        scope = coroutineScope,
                        onSuccess = {
                            isAuthBusy = false
                            authErrorMessage = null
                            showAuthDialog = false
                            val nextAction = pendingCloudAction
                            pendingCloudAction = PendingCloudAction.NONE
                            executeCloudAction(nextAction)
                        },
                        onCancelled = {
                            isAuthBusy = false
                            authErrorMessage = "Giriş ləğv edildi. Yenidən cəhd edə və ya brauzerlə daxil ola bilərsiniz."
                        },
                        onError = { err ->
                            isAuthBusy = false
                            authErrorMessage = err
                        },
                    )
                },
                onBrowserSignInClick = {
                    isAuthBusy = true
                    authErrorMessage = null
                    TestBazaAuthHelper.signInWithBrowserFallback(
                        context = context,
                        scope = coroutineScope,
                        onSuccess = {
                            isAuthBusy = false
                            authErrorMessage = null
                            showAuthDialog = false
                            val nextAction = pendingCloudAction
                            pendingCloudAction = PendingCloudAction.NONE
                            executeCloudAction(nextAction)
                        },
                        onError = { err ->
                            isAuthBusy = false
                            authErrorMessage = err
                        },
                    )
                },
                onDismiss = {
                    if (!isAuthBusy) {
                        showAuthDialog = false
                        pendingCloudAction = PendingCloudAction.NONE
                    }
                },
            )
        }

        if (showPublishExamDialog) {
            val effectiveCount = uiState.examConfig.questionCount.coerceIn(
                1,
                minOf(uiState.totalCount.coerceAtLeast(1), ExamConfig.MAX_QUESTIONS),
            )
            PublishExamDialog(
                defaultTitle = uiState.activeBank?.title.orEmpty(),
                defaultOwnerName = uiState.cloudUserName.orEmpty(),
                questionCount = effectiveCount,
                durationMinutes = uiState.examConfig.durationMinutes,
                isBusy = uiState.isCloudBusy,
                publishedExam = uiState.latestPublishedExam,
                onPublish = { title, ownerName ->
                    viewModel.publishActiveBankAsSharedExam(
                        examTitle = title,
                        ownerDisplayName = ownerName,
                        onPublished = {
                            viewModel.showStatus("İmtahan paylaşım kodu yaradıldı.", isError = false)
                        },
                    )
                },
                onCopyCode = { code ->
                    viewModel.showStatus("Kod kopyalandı: $code", isError = false)
                },
                onDismiss = { showPublishExamDialog = false },
            )
        }

        if (showJoinExamDialog) {
            JoinSharedExamDialog(
                defaultParticipantName = uiState.participantDisplayName.ifBlank { uiState.cloudUserName.orEmpty() },
                isBusy = uiState.isCloudBusy,
                previewExam = uiState.fetchedPreviewExam,
                onLookupCode = viewModel::lookupSharedExamByCode,
                onStartExam = { exam, participantName, consentGranted ->
                    showJoinExamDialog = false
                    viewModel.startSharedExamSession(
                        sharedExam = exam,
                        participantName = participantName,
                        consentGranted = consentGranted,
                    )
                },
                onDismiss = { showJoinExamDialog = false },
            )
        }

        if (showMySharedExamsDialog) {
            MySharedExamsDialog(
                sharedExams = uiState.mySharedExams,
                inspectedExamId = uiState.inspectedSharedExamId,
                attempts = uiState.inspectedAttempts,
                attemptsError = uiState.attemptsErrorMessage,
                onSelectExam = viewModel::inspectSharedExamAttempts,
                onDeleteExam = viewModel::deleteSharedExam,
                onCopyCode = { code ->
                    viewModel.showStatus("Kod kopyalandı: $code", isError = false)
                },
                onDismiss = {
                    viewModel.inspectSharedExamAttempts(null)
                    showMySharedExamsDialog = false
                },
            )
        }

        if (showKeyEditor && uiState.rawQuestions.isNotEmpty() && !uiState.isExamActive) {
            KeyEditorModalDialog(
                questions = uiState.rawQuestions,
                keyedCount = uiState.keyedCount,
                onSelectAnswer = { q, opt ->
                    viewModel.updateSingleAnswerKey(q, opt, reshuffleNow = false)
                },
                onSelectAllFirst = viewModel::selectAllFirstOptions,
                onStartShuffledQuiz = viewModel::startQuizFromKeyEditor,
                onDismiss = { showKeyEditor = false },
            )
        }

        if (showSingleKeyDialog && !uiState.isExamActive) {
            val activeQuestion = uiState.currentSessionItem?.question
            if (activeQuestion != null) {
                SingleQuestionKeyDialog(
                    question = activeQuestion,
                    onSaveAndShuffle = { opt ->
                        viewModel.updateSingleAnswerKey(activeQuestion, opt, reshuffleNow = true)
                    },
                    onDismiss = { showSingleKeyDialog = false },
                )
            }
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
