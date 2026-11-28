package com.aistudio.testbaza.core.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.testbaza.core.data.QuizRepository
import com.aistudio.testbaza.core.data.local.QuizDatabase
import com.aistudio.testbaza.core.data.local.QuizPreferencesStore
import com.aistudio.testbaza.core.data.remote.SharedExamRepository
import com.aistudio.testbaza.core.data.remote.TestBazaAuthHelper
import com.aistudio.testbaza.core.data.remote.testBazaAuthStateFlow
import com.aistudio.testbaza.core.domain.ActiveExamUiState
import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.ChunkOption
import com.aistudio.testbaza.core.domain.ExamConfig
import com.aistudio.testbaza.core.domain.HtmlExporter
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.QuizQuestion
import com.aistudio.testbaza.core.domain.SessionQuestionItem
import com.aistudio.testbaza.core.domain.SharedExam
import com.aistudio.testbaza.core.domain.SharedExamAttempt
import com.aistudio.testbaza.core.domain.StudyMode
import com.aistudio.testbaza.core.domain.TestBazaThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TestBazaUiState(
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val themeMode: TestBazaThemeMode = TestBazaThemeMode.DARK,
    val banks: List<QuizBank> = emptyList(),
    val activeBank: QuizBank? = null,
    val rawQuestions: List<QuizQuestion> = emptyList(),
    val mode: StudyMode = StudyMode.QUIZ,
    val selectedChunkIndex: Int? = null,
    val chunkOptions: List<ChunkOption> = emptyList(),
    val isShuffled: Boolean = true,
    val sessionItems: List<SessionQuestionItem> = emptyList(),
    val currentIndex: Int = 0,
    val flashcardIndex: Int = 0,
    val isFlashcardFlipped: Boolean = false,
    val isSessionFinished: Boolean = false,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val streakCount: Int = 0,
    val importRule: AnswerRule = AnswerRule.AUTO,
    val statusMessage: String? = null,
    val isStatusError: Boolean = false,
    val examConfig: ExamConfig = ExamConfig(),
    val activeExam: ActiveExamUiState = ActiveExamUiState(),
    val participantDisplayName: String = "",
    val isCloudAvailable: Boolean = false,
    val cloudUserUid: String? = null,
    val cloudUserName: String? = null,
    val cloudUserEmail: String? = null,
    val isCloudBusy: Boolean = false,
    val mySharedExams: List<SharedExam> = emptyList(),
    val inspectedSharedExamId: String? = null,
    val inspectedAttempts: List<SharedExamAttempt> = emptyList(),
    val attemptsErrorMessage: String? = null,
    val fetchedPreviewExam: SharedExam? = null,
    val latestPublishedExam: SharedExam? = null,
) {
    val totalCount: Int get() = rawQuestions.size
    val examCount: Int get() = rawQuestions.count { it.isExam }
    val weakCount: Int get() = rawQuestions.count { it.masteryStatus == QuestionMasteryStatus.WEAK }
    val keyedCount: Int get() = rawQuestions.count { it.hasConfirmedKey }
    val currentSessionItem: SessionQuestionItem? get() = sessionItems.getOrNull(currentIndex)
    val currentFlashcardItem: SessionQuestionItem? get() = sessionItems.getOrNull(flashcardIndex)
    val isExamActive: Boolean get() = activeExam.active
    val isSignedIn: Boolean get() = !cloudUserUid.isNullOrBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestBazaViewModel(
    application: Application,
    private val firestoreDatabaseId: String = "",
) : AndroidViewModel(application) {

    private val database = QuizDatabase.getInstance(application)
    private val repository = QuizRepository(application, database.quizDao())
    private val prefs = QuizPreferencesStore(application)

    private val firebaseReady = TestBazaAuthHelper.isFirebaseReady(application) && firestoreDatabaseId.isNotBlank()
    private val sharedExamRepo: SharedExamRepository? = if (firebaseReady) {
        runCatching {
            SharedExamRepository(
                db = FirebaseFirestore.getInstance(firestoreDatabaseId),
                auth = Firebase.auth,
            )
        }.onFailure { err ->
            Log.w(TAG, "SharedExamRepository init skipped: ${err.message}")
        }.getOrNull()
    } else {
        null
    }

    private val _initialized = MutableStateFlow(false)
    private val _activeBankId = MutableStateFlow<Long?>(null)
    private val _sessionState = MutableStateFlow(SessionState())
    private val _cloudState = MutableStateFlow(CloudUiInternalState(isCloudAvailable = sharedExamRepo != null))
    private val _inspectedExamId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(TestBazaUiState(isCloudAvailable = sharedExamRepo != null))
    val uiState: StateFlow<TestBazaUiState> = _uiState.asStateFlow()

    private val banksState = repository.banksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    private val questionsFlow = _activeBankId.flatMapLatest { bankId ->
        if (bankId == null) flowOf(emptyList())
        else repository.observeQuestions(bankId)
    }

    private var examTickerJob: Job? = null
    private var perQuestionElapsedMs = LongArray(0)
    private var activeQuestionStartedAtMs: Long = 0L

    init {
        viewModelScope.launch {
            repository.removeBundledDefaultBank()
            val currentBanks = repository.banksFlow.first()
            val savedId = prefs.activeBankIdFlow.first()
            val validSaved = currentBanks.firstOrNull { it.id == savedId }?.id
            val initialId = validSaved ?: currentBanks.firstOrNull()?.id
            if (initialId != null) prefs.setActiveBankId(initialId) else prefs.clearActiveBankId()
            _activeBankId.value = initialId
            _initialized.value = true
        }

        if (sharedExamRepo != null) {
            viewModelScope.launch {
                Firebase.auth.testBazaAuthStateFlow().collect { user ->
                    val uid = user?.uid?.takeIf { it.isNotBlank() }
                    _cloudState.update {
                        it.copy(
                            uid = uid,
                            displayName = user?.displayName,
                            email = user?.email,
                        )
                    }
                    if (uid == null) {
                        _inspectedExamId.value = null
                    }
                }
            }

            viewModelScope.launch {
                _cloudState
                    .flatMapLatest { cloud ->
                        val uid = cloud.uid
                        if (uid.isNullOrBlank()) {
                            flowOf(emptyList())
                        } else {
                            sharedExamRepo.observeMySharedExams(uid)
                                .catch { error ->
                                    val fsErr = generateSequence(error) { it.cause }
                                        .filterIsInstance<FirebaseFirestoreException>()
                                        .firstOrNull()
                                    if (fsErr == null && error is CancellationException) throw error
                                    Log.w(TAG, "observeMySharedExams error", fsErr ?: error)
                                    emit(emptyList())
                                }
                        }
                    }
                    .collect { exams ->
                        _cloudState.update { it.copy(mySharedExams = exams) }
                    }
            }

            viewModelScope.launch {
                combine(_cloudState, _inspectedExamId) { cloud, examId ->
                    Pair(cloud.uid, examId)
                }.flatMapLatest { (uid, examId) ->
                    if (uid.isNullOrBlank() || examId.isNullOrBlank()) {
                        flowOf(AttemptStreamResult(emptyList(), null))
                    } else {
                        sharedExamRepo.observeConsentedAttempts(examId, uid)
                            .catch { error ->
                                val fsErr = generateSequence(error) { it.cause }
                                    .filterIsInstance<FirebaseFirestoreException>()
                                    .firstOrNull()
                                if (fsErr == null && error is CancellationException) throw error
                                val root = fsErr ?: error
                                Log.w(TAG, "observeConsentedAttempts error", root)
                                _cloudState.update {
                                    it.copy(attemptsErrorMessage = root.localizedMessage ?: "Nəticələr oxuna bilmədi.")
                                }
                                emit(emptyList())
                            }
                            .let { flow ->
                                combine(flow, flowOf(examId)) { list, _ ->
                                    AttemptStreamResult(list, null)
                                }
                            }
                    }
                }.collect { res ->
                    _cloudState.update {
                        it.copy(
                            inspectedAttempts = res.attempts,
                            attemptsErrorMessage = res.error ?: it.attemptsErrorMessage,
                        )
                    }
                }
            }
        }

        // Sync practice session items when not in an active timed exam
        viewModelScope.launch {
            combine(
                _initialized,
                _activeBankId,
                questionsFlow,
                prefs.shuffleEnabledFlow,
            ) { ready, activeId, questions, shuffleEnabled ->
                SessionSyncInput(ready, activeId, questions, shuffleEnabled)
            }.collect { input ->
                if (!input.ready) return@collect
                if (_sessionState.value.exam.active) return@collect
                if (input.activeId == null) {
                    _sessionState.update {
                        it.copy(
                            builtForBankId = null,
                            builtForShuffle = input.shuffleEnabled,
                            forceRebuild = false,
                            items = emptyList(),
                            currentIndex = 0,
                            flashcardIndex = 0,
                            isFlashcardFlipped = false,
                            isSessionFinished = false,
                            correctCount = 0,
                            incorrectCount = 0,
                        )
                    }
                    return@collect
                }
                if (input.questions.isNotEmpty() && input.questions.first().bankId != input.activeId) {
                    return@collect
                }

                _sessionState.update { prev ->
                    val shouldRebuild = prev.builtForBankId != input.activeId ||
                        prev.builtForShuffle != input.shuffleEnabled ||
                        prev.forceRebuild
                    if (!shouldRebuild) {
                        val qMap = input.questions.associateBy { it.rowId }
                        val updatedItems = prev.items.mapNotNull { item ->
                            val updatedQ = qMap[item.question.rowId] ?: return@mapNotNull null
                            item.copy(question = updatedQ)
                        }
                        prev.copy(
                            items = updatedItems,
                            currentIndex = prev.currentIndex.coerceIn(0, maxOf(0, updatedItems.lastIndex)),
                            flashcardIndex = prev.flashcardIndex.coerceIn(0, maxOf(0, updatedItems.lastIndex)),
                        )
                    } else {
                        val filtered = QuizEngine.filterBaseQuestions(
                            allQuestions = input.questions,
                            mode = prev.mode,
                            selectedChunkIndex = prev.selectedChunkIndex,
                        )
                        val builtItems = QuizEngine.buildSessionItems(filtered, input.shuffleEnabled)
                        prev.copy(
                            builtForBankId = input.activeId,
                            builtForShuffle = input.shuffleEnabled,
                            forceRebuild = false,
                            items = builtItems,
                            currentIndex = 0,
                            flashcardIndex = 0,
                            isFlashcardFlipped = false,
                            isSessionFinished = false,
                            correctCount = 0,
                            incorrectCount = 0,
                        )
                    }
                }
            }
        }

        // Pure UI State projection
        viewModelScope.launch {
            combine(
                _initialized,
                banksState,
                _activeBankId,
                questionsFlow,
                prefs.themeModeFlow,
                prefs.shuffleEnabledFlow,
                prefs.examConfigFlow,
                prefs.participantDisplayNameFlow,
                _sessionState,
                _cloudState,
                _inspectedExamId,
            ) { values ->
                val ready = values[0] as Boolean
                @Suppress("UNCHECKED_CAST")
                val banks = values[1] as List<QuizBank>
                val activeId = values[2] as Long?
                @Suppress("UNCHECKED_CAST")
                val rawQuestions = values[3] as List<QuizQuestion>
                val theme = values[4] as TestBazaThemeMode
                val shuffleEnabled = values[5] as Boolean
                val examConfig = values[6] as ExamConfig
                val participantName = values[7] as String
                val session = values[8] as SessionState
                val cloud = values[9] as CloudUiInternalState
                val inspectedId = values[10] as String?

                val resolvedBank = if (activeId != null) banks.firstOrNull { it.id == activeId } else null
                val validQuestions = if (activeId == null) emptyList() else rawQuestions.filter { it.bankId == activeId }

                TestBazaUiState(
                    isLoading = !ready,
                    isImporting = session.isImporting,
                    themeMode = theme,
                    banks = banks,
                    activeBank = resolvedBank,
                    rawQuestions = validQuestions,
                    mode = session.mode,
                    selectedChunkIndex = session.selectedChunkIndex,
                    chunkOptions = QuizEngine.buildChunkOptions(validQuestions.size),
                    isShuffled = shuffleEnabled,
                    sessionItems = session.items,
                    currentIndex = session.currentIndex,
                    flashcardIndex = session.flashcardIndex,
                    isFlashcardFlipped = session.isFlashcardFlipped,
                    isSessionFinished = session.isSessionFinished,
                    correctCount = session.correctCount,
                    incorrectCount = session.incorrectCount,
                    streakCount = session.streakCount,
                    importRule = session.importRule,
                    statusMessage = session.statusMessage,
                    isStatusError = session.isStatusError,
                    examConfig = examConfig,
                    activeExam = session.exam,
                    participantDisplayName = participantName,
                    isCloudAvailable = cloud.isCloudAvailable,
                    cloudUserUid = cloud.uid,
                    cloudUserName = cloud.displayName,
                    cloudUserEmail = cloud.email,
                    isCloudBusy = cloud.isBusy,
                    mySharedExams = cloud.mySharedExams,
                    inspectedSharedExamId = inspectedId,
                    inspectedAttempts = cloud.inspectedAttempts,
                    attemptsErrorMessage = cloud.attemptsErrorMessage,
                    fetchedPreviewExam = cloud.fetchedPreviewExam,
                    latestPublishedExam = cloud.latestPublishedExam,
                )
            }.collect { nextState ->
                _uiState.value = nextState
            }
        }
    }

    // --- Exam Configuration ---

    fun setExamQuestionCount(count: Int) {
        viewModelScope.launch { prefs.setExamQuestionCount(count) }
    }

    fun setExamDurationMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setExamDurationMinutes(minutes) }
    }

    fun setParticipantDisplayName(name: String) {
        viewModelScope.launch { prefs.setParticipantDisplayName(name) }
    }

    // --- Local & Shared Timed Exam Engine ---

    fun startLocalTimedExam() {
        val current = _uiState.value
        if (current.rawQuestions.isEmpty()) {
            setStatus("İmtahan başlatmaq üçün əvvəlcə sual bazası yükləyin.", isError = true)
            return
        }
        val selected = QuizEngine.selectExamQuestions(
            allQuestions = current.rawQuestions,
            requestedCount = current.examConfig.questionCount,
            shuffle = current.isShuffled,
        )
        val builtItems = QuizEngine.buildSessionItems(selected, shuffle = current.isShuffled)
        startTimedExamInternal(
            title = current.activeBank?.title ?: "Sınaq İmtahanı",
            examCode = null,
            durationMinutes = current.examConfig.durationMinutes,
            items = builtItems,
            sharedExam = null,
            participantName = "",
            consentGranted = false,
        )
    }

    fun startSharedExamSession(
        sharedExam: SharedExam,
        participantName: String,
        consentGranted: Boolean,
    ) {
        if (sharedExam.questions.isEmpty()) {
            setStatus("Bu paylaşılan imtahanda sual tapılmadı.", isError = true)
            return
        }
        viewModelScope.launch {
            if (participantName.isNotBlank()) prefs.setParticipantDisplayName(participantName)
        }
        val builtItems = QuizEngine.buildSessionItemsFromSharedExam(sharedExam)
        val duration = sharedExam.durationMinutes.toInt().coerceIn(
            ExamConfig.MIN_DURATION_MINUTES,
            ExamConfig.MAX_DURATION_MINUTES,
        )
        startTimedExamInternal(
            title = sharedExam.title,
            examCode = sharedExam.id,
            durationMinutes = duration,
            items = builtItems,
            sharedExam = sharedExam,
            participantName = participantName.trim(),
            consentGranted = consentGranted,
        )
    }

    private fun startTimedExamInternal(
        title: String,
        examCode: String?,
        durationMinutes: Int,
        items: List<SessionQuestionItem>,
        sharedExam: SharedExam?,
        participantName: String,
        consentGranted: Boolean,
    ) {
        examTickerJob?.cancel()
        perQuestionElapsedMs = LongArray(items.size) { 0L }
        activeQuestionStartedAtMs = SystemClock.elapsedRealtime()
        val limitSeconds = durationMinutes.coerceIn(1, 180) * 60

        _sessionState.update {
            it.copy(
                mode = StudyMode.QUIZ,
                selectedChunkIndex = null,
                items = items,
                currentIndex = 0,
                flashcardIndex = 0,
                isFlashcardFlipped = false,
                isSessionFinished = false,
                correctCount = 0,
                incorrectCount = 0,
                streakCount = 0,
                exam = ActiveExamUiState(
                    active = true,
                    title = title,
                    examCode = examCode,
                    durationMinutes = durationMinutes,
                    remainingSeconds = limitSeconds,
                    currentQuestionElapsedSeconds = 0L,
                    totalElapsedSeconds = 0L,
                    timedOut = false,
                    sharedExam = sharedExam,
                    participantName = participantName,
                    shareConsentGranted = consentGranted,
                    perQuestionSeconds = List(items.size) { 0L },
                ),
            )
        }

        examTickerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val session = _sessionState.value
                if (!session.exam.active || session.isSessionFinished) break
                val nextRemaining = (session.exam.remainingSeconds - 1).coerceAtLeast(0)
                val currentIdx = session.currentIndex
                val liveQuestionDeltaMs = if (activeQuestionStartedAtMs > 0L) {
                    (SystemClock.elapsedRealtime() - activeQuestionStartedAtMs).coerceAtLeast(0L)
                } else 0L
                val storedMs = perQuestionElapsedMs.getOrElse(currentIdx) { 0L }
                val currentQSec = (storedMs + liveQuestionDeltaMs) / 1000L

                _sessionState.update { s ->
                    if (!s.exam.active || s.isSessionFinished) s
                    else s.copy(
                        exam = s.exam.copy(
                            remainingSeconds = nextRemaining,
                            totalElapsedSeconds = s.exam.totalElapsedSeconds + 1L,
                            currentQuestionElapsedSeconds = currentQSec,
                        )
                    )
                }
                if (nextRemaining == 0) {
                    finishActiveExam(timedOut = true)
                    break
                }
            }
        }
    }

    private fun flushCurrentQuestionElapsedTime() {
        val session = _sessionState.value
        if (!session.exam.active || session.isSessionFinished) return
        val idx = session.currentIndex
        if (idx in perQuestionElapsedMs.indices && activeQuestionStartedAtMs > 0L) {
            val now = SystemClock.elapsedRealtime()
            val delta = (now - activeQuestionStartedAtMs).coerceAtLeast(0L)
            perQuestionElapsedMs[idx] += delta
            activeQuestionStartedAtMs = now
        }
    }

    private fun resetQuestionClockForIndex(newIndex: Int) {
        activeQuestionStartedAtMs = SystemClock.elapsedRealtime()
        val storedSec = perQuestionElapsedMs.getOrElse(newIndex) { 0L } / 1000L
        _sessionState.update { s ->
            if (!s.exam.active) s
            else s.copy(exam = s.exam.copy(currentQuestionElapsedSeconds = storedSec))
        }
    }

    fun finishActiveExam(timedOut: Boolean = false) {
        val session = _sessionState.value
        if (!session.exam.active || session.isSessionFinished) return
        flushCurrentQuestionElapsedTime()
        examTickerJob?.cancel()
        examTickerJob = null

        val perQuestionSeconds = perQuestionElapsedMs.map { ms ->
            if (ms <= 0L) 0L else maxOf(1L, (ms + 500L) / 1000L)
        }
        val summary = QuizEngine.buildExamEvaluationSummary(session.items, perQuestionSeconds)
        val totalElapsed = maxOf(session.exam.totalElapsedSeconds, perQuestionSeconds.sum())

        _sessionState.update { s ->
            s.copy(
                isSessionFinished = true,
                exam = s.exam.copy(
                    timedOut = timedOut,
                    totalElapsedSeconds = totalElapsed,
                    perQuestionSeconds = perQuestionSeconds,
                    evaluationSummary = summary,
                ),
            )
        }

        val updatedExamState = _sessionState.value.exam
        if (updatedExamState.sharedExam != null && updatedExamState.shareConsentGranted) {
            uploadConsentedAttemptIfAllowed()
        }
    }

    fun setSharedExamConsentAndUpload(consentGranted: Boolean) {
        val currentExam = _sessionState.value.exam
        if (currentExam.sharedExam == null) return
        if (!consentGranted) {
            val attemptId = currentExam.uploadedAttemptId
            val examId = currentExam.sharedExam.id
            _sessionState.update {
                it.copy(
                    exam = it.exam.copy(
                        shareConsentGranted = false,
                        attemptUploaded = false,
                        uploadedAttemptId = null,
                        uploadErrorMessage = null,
                    )
                )
            }
            if (!attemptId.isNullOrBlank()) {
                viewModelScope.launch {
                    sharedExamRepo?.deleteAttempt(examId, attemptId)
                }
            }
            return
        }
        _sessionState.update {
            it.copy(exam = it.exam.copy(shareConsentGranted = true, uploadErrorMessage = null))
        }
        if (_sessionState.value.isSessionFinished) {
            uploadConsentedAttemptIfAllowed()
        }
    }

    fun uploadConsentedAttemptIfAllowed() {
        val repo = sharedExamRepo ?: return
        val session = _sessionState.value
        val examState = session.exam
        val sharedExam = examState.sharedExam ?: return
        val summary = examState.evaluationSummary ?: return
        if (!examState.shareConsentGranted || examState.attemptUploaded || examState.attemptUploading) return

        viewModelScope.launch {
            _sessionState.update {
                it.copy(exam = it.exam.copy(attemptUploading = true, uploadErrorMessage = null))
            }
            val result = repo.submitConsentedAttempt(
                exam = sharedExam,
                participantName = examState.participantName,
                summary = summary,
                totalElapsedSeconds = examState.totalElapsedSeconds,
                durationLimitSeconds = (examState.durationMinutes * 60L).coerceIn(60L, 10800L),
                timedOut = examState.timedOut,
                consentGranted = true,
            )
            result.fold(
                onSuccess = { attemptId ->
                    _sessionState.update {
                        it.copy(
                            exam = it.exam.copy(
                                attemptUploading = false,
                                attemptUploaded = true,
                                uploadedAttemptId = attemptId,
                                uploadErrorMessage = null,
                            ),
                            statusMessage = "Nəticəniz icazənizlə paylaşan şəxsə göndərildi.",
                            isStatusError = false,
                        )
                    }
                },
                onFailure = { err ->
                    _sessionState.update {
                        it.copy(
                            exam = it.exam.copy(
                                attemptUploading = false,
                                uploadErrorMessage = err.message ?: "Nəticə göndərilə bilmədi.",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun exitExamMode() {
        examTickerJob?.cancel()
        examTickerJob = null
        perQuestionElapsedMs = LongArray(0)
        activeQuestionStartedAtMs = 0L
        returnToFullQuiz()
    }

    // --- Cloud Exam Sharing & Results Inspection ---

    fun publishActiveBankAsSharedExam(
        examTitle: String,
        ownerDisplayName: String,
        onPublished: (SharedExam) -> Unit,
    ) {
        val repo = sharedExamRepo
        if (repo == null) {
            setStatus("Bulud paylaşımı bu quruluşda aktiv deyil.", isError = true)
            return
        }
        val state = _uiState.value
        if (!state.isSignedIn) {
            setStatus("İmtahanı paylaşmaq üçün Google hesabına daxil olun.", isError = true)
            return
        }
        val selectedQuestions = QuizEngine.selectExamQuestions(
            allQuestions = state.rawQuestions,
            requestedCount = state.examConfig.questionCount,
            shuffle = state.isShuffled,
        )
        if (selectedQuestions.isEmpty()) {
            setStatus("Paylaşmaq üçün əvvəlcə sual bazası yükləyin.", isError = true)
            return
        }
        viewModelScope.launch {
            _cloudState.update { it.copy(isBusy = true) }
            val result = repo.publishSharedExam(
                title = examTitle,
                sourceBankTitle = state.activeBank?.title.orEmpty(),
                ownerName = ownerDisplayName,
                questions = selectedQuestions,
                durationMinutes = state.examConfig.durationMinutes,
                shuffleOptions = state.isShuffled,
            )
            _cloudState.update { it.copy(isBusy = false) }
            result.fold(
                onSuccess = { sharedExam ->
                    _cloudState.update { it.copy(latestPublishedExam = sharedExam) }
                    onPublished(sharedExam)
                },
                onFailure = { err ->
                    setStatus(err.message ?: "İmtahan paylaşıla bilmədi.", isError = true)
                },
            )
        }
    }

    fun lookupSharedExamByCode(code: String) {
        val repo = sharedExamRepo ?: run {
            setStatus("Bulud xidməti aktiv deyil.", isError = true)
            return
        }
        viewModelScope.launch {
            _cloudState.update { it.copy(isBusy = true, fetchedPreviewExam = null) }
            val result = repo.fetchSharedExamByCode(code)
            _cloudState.update { it.copy(isBusy = false) }
            result.fold(
                onSuccess = { exam ->
                    _cloudState.update { it.copy(fetchedPreviewExam = exam) }
                },
                onFailure = { err ->
                    setStatus(err.message ?: "İmtahan kodu tapılmadı.", isError = true)
                },
            )
        }
    }

    fun clearFetchedPreviewExam() {
        _cloudState.update { it.copy(fetchedPreviewExam = null) }
    }

    fun clearLatestPublishedExam() {
        _cloudState.update { it.copy(latestPublishedExam = null) }
    }

    fun inspectSharedExamAttempts(examId: String?) {
        _cloudState.update { it.copy(attemptsErrorMessage = null) }
        _inspectedExamId.value = examId
    }

    fun deleteSharedExam(examId: String) {
        val repo = sharedExamRepo ?: return
        viewModelScope.launch {
            _cloudState.update { it.copy(isBusy = true) }
            val res = repo.deleteSharedExam(examId)
            _cloudState.update { it.copy(isBusy = false) }
            res.fold(
                onSuccess = {
                    if (_inspectedExamId.value == examId) {
                        _inspectedExamId.value = null
                    }
                    setStatus("Paylaşılan imtahan silindi.", isError = false)
                },
                onFailure = { err ->
                    setStatus(err.message ?: "İmtahan silinə bilmədi.", isError = true)
                },
            )
        }
    }

    // --- Study Mode, Navigation & Questions ---

    fun switchMode(newMode: StudyMode) {
        if (_sessionState.value.exam.active) {
            exitExamMode()
        }
        val current = _uiState.value
        if (newMode == StudyMode.EXAM && current.examCount == 0) {
            setStatus("Hələ seçilmiş (ulduzlu) sual yoxdur. Sual kartındakı ulduz işarəsi ilə əlavə edin.", isError = true)
            return
        }
        if (newMode == StudyMode.WEAK && current.weakCount == 0) {
            setStatus("Hazırda təkrar olunacaq səhv cavab yoxdur.", isError = false)
            return
        }
        _sessionState.update { state ->
            val nextChunk = if (newMode == StudyMode.EXAM || newMode == StudyMode.WEAK) null else state.selectedChunkIndex
            val filtered = QuizEngine.filterBaseQuestions(
                allQuestions = current.rawQuestions,
                mode = newMode,
                selectedChunkIndex = nextChunk,
            )
            val builtItems = QuizEngine.buildSessionItems(filtered, current.isShuffled)
            state.copy(
                mode = newMode,
                selectedChunkIndex = nextChunk,
                builtForBankId = current.activeBank?.id,
                builtForShuffle = current.isShuffled,
                forceRebuild = false,
                items = builtItems,
                currentIndex = 0,
                flashcardIndex = 0,
                isFlashcardFlipped = false,
                isSessionFinished = false,
                correctCount = 0,
                incorrectCount = 0,
                exam = ActiveExamUiState(),
            )
        }
    }

    fun selectChunk(chunkIndex: Int?) {
        if (_sessionState.value.exam.active) return
        val current = _uiState.value
        _sessionState.update { state ->
            val nextMode = if (state.mode == StudyMode.EXAM || state.mode == StudyMode.WEAK) StudyMode.QUIZ else state.mode
            val filtered = QuizEngine.filterBaseQuestions(
                allQuestions = current.rawQuestions,
                mode = nextMode,
                selectedChunkIndex = chunkIndex,
            )
            val builtItems = QuizEngine.buildSessionItems(filtered, current.isShuffled)
            state.copy(
                mode = nextMode,
                selectedChunkIndex = chunkIndex,
                builtForBankId = current.activeBank?.id,
                builtForShuffle = current.isShuffled,
                forceRebuild = false,
                items = builtItems,
                currentIndex = 0,
                flashcardIndex = 0,
                isFlashcardFlipped = false,
                isSessionFinished = false,
                correctCount = 0,
                incorrectCount = 0,
            )
        }
    }

    fun toggleShuffle() {
        if (_sessionState.value.exam.active) return
        val next = !_uiState.value.isShuffled
        viewModelScope.launch {
            prefs.setShuffleEnabled(next)
            _sessionState.update { it.copy(forceRebuild = true) }
        }
    }

    fun selectOption(optionIndex: Int) {
        val currentSession = _sessionState.value
        val item = currentSession.items.getOrNull(currentSession.currentIndex) ?: return
        if (item.isAnswered || optionIndex in item.disabledOptions) return

        val isCorrect = optionIndex == item.correctIdx
        val updatedItems = currentSession.items.toMutableList()
        updatedItems[currentSession.currentIndex] = item.copy(
            selectedIdx = optionIndex,
            isCorrect = isCorrect,
        )

        _sessionState.update {
            it.copy(
                items = updatedItems,
                correctCount = if (isCorrect) it.correctCount + 1 else it.correctCount,
                incorrectCount = if (!isCorrect) it.incorrectCount + 1 else it.incorrectCount,
                streakCount = if (isCorrect) it.streakCount + 1 else 0,
            )
        }

        if (!currentSession.exam.isSharedExam && item.question.rowId > 0L) {
            viewModelScope.launch {
                repository.recordAnswerOutcome(item.question, isCorrect)
            }
        }
    }

    fun useJoker5050() {
        val currentSession = _sessionState.value
        val item = currentSession.items.getOrNull(currentSession.currentIndex) ?: return
        if (item.isAnswered) return
        val updatedItem = QuizEngine.applyJoker5050(item)
        if (updatedItem == item) return
        val updatedItems = currentSession.items.toMutableList()
        updatedItems[currentSession.currentIndex] = updatedItem
        _sessionState.update { it.copy(items = updatedItems) }
    }

    fun markCurrentMastered() {
        val item = _uiState.value.currentSessionItem ?: return
        if (item.question.rowId <= 0L) return
        viewModelScope.launch {
            repository.markMastered(item.question)
            setStatus("Sual #${item.question.questionNumber} mənimsənilib kimi qeyd olundu.", isError = false)
        }
    }

    fun toggleCurrentStar() {
        val item = _uiState.value.currentSessionItem ?: return
        if (item.question.rowId <= 0L) return
        viewModelScope.launch {
            repository.toggleStar(item.question)
        }
    }

    fun nextQuestion() {
        val current = _sessionState.value
        if (current.items.isEmpty()) return
        if (current.exam.active) {
            if (current.currentIndex < current.items.lastIndex) {
                flushCurrentQuestionElapsedTime()
                val nextIdx = current.currentIndex + 1
                _sessionState.update { it.copy(currentIndex = nextIdx) }
                resetQuestionClockForIndex(nextIdx)
            } else {
                finishActiveExam(timedOut = false)
            }
        } else {
            _sessionState.update { state ->
                if (state.currentIndex < state.items.lastIndex) {
                    state.copy(currentIndex = state.currentIndex + 1)
                } else {
                    state.copy(isSessionFinished = true)
                }
            }
        }
    }

    fun prevQuestion() {
        val current = _sessionState.value
        if (current.currentIndex <= 0) return
        if (current.exam.active) {
            flushCurrentQuestionElapsedTime()
            val prevIdx = current.currentIndex - 1
            _sessionState.update { it.copy(currentIndex = prevIdx) }
            resetQuestionClockForIndex(prevIdx)
        } else {
            _sessionState.update { state ->
                state.copy(currentIndex = state.currentIndex - 1)
            }
        }
    }

    fun jumpToQuestionIndex(index: Int) {
        val current = _sessionState.value
        val clamped = index.coerceIn(0, maxOf(0, current.items.lastIndex))
        if (current.mode == StudyMode.FLASHCARD) {
            _sessionState.update { it.copy(flashcardIndex = clamped, isFlashcardFlipped = false) }
            return
        }
        if (current.exam.active) {
            if (!current.isSessionFinished) {
                flushCurrentQuestionElapsedTime()
                _sessionState.update { it.copy(currentIndex = clamped) }
                resetQuestionClockForIndex(clamped)
            }
        } else {
            _sessionState.update { it.copy(currentIndex = clamped, isSessionFinished = false) }
        }
    }

    fun flipFlashcard() {
        _sessionState.update { it.copy(isFlashcardFlipped = !it.isFlashcardFlipped) }
    }

    fun nextFlashcard() {
        _sessionState.update { state ->
            if (state.flashcardIndex < state.items.lastIndex) {
                state.copy(flashcardIndex = state.flashcardIndex + 1, isFlashcardFlipped = false)
            } else state
        }
    }

    fun prevFlashcard() {
        _sessionState.update { state ->
            if (state.flashcardIndex > 0) {
                state.copy(flashcardIndex = state.flashcardIndex - 1, isFlashcardFlipped = false)
            } else state
        }
    }

    fun restartSession() {
        val currentState = _sessionState.value
        if (currentState.exam.active) {
            val sharedExam = currentState.exam.sharedExam
            if (sharedExam != null) {
                startSharedExamSession(
                    sharedExam = sharedExam,
                    participantName = currentState.exam.participantName,
                    consentGranted = currentState.exam.shareConsentGranted,
                )
            } else {
                startLocalTimedExam()
            }
            return
        }
        val current = _uiState.value
        _sessionState.update { state ->
            val filtered = QuizEngine.filterBaseQuestions(
                allQuestions = current.rawQuestions,
                mode = state.mode,
                selectedChunkIndex = state.selectedChunkIndex,
            )
            state.copy(
                builtForBankId = current.activeBank?.id,
                builtForShuffle = current.isShuffled,
                forceRebuild = false,
                items = QuizEngine.buildSessionItems(filtered, current.isShuffled),
                currentIndex = 0,
                flashcardIndex = 0,
                isFlashcardFlipped = false,
                isSessionFinished = false,
                correctCount = 0,
                incorrectCount = 0,
            )
        }
    }

    fun returnToFullQuiz() {
        examTickerJob?.cancel()
        examTickerJob = null
        perQuestionElapsedMs = LongArray(0)
        activeQuestionStartedAtMs = 0L
        val current = _uiState.value
        _sessionState.update { state ->
            state.copy(
                mode = StudyMode.QUIZ,
                selectedChunkIndex = null,
                builtForBankId = current.activeBank?.id,
                builtForShuffle = current.isShuffled,
                forceRebuild = false,
                items = QuizEngine.buildSessionItems(current.rawQuestions, current.isShuffled),
                currentIndex = 0,
                flashcardIndex = 0,
                isFlashcardFlipped = false,
                isSessionFinished = false,
                correctCount = 0,
                incorrectCount = 0,
                exam = ActiveExamUiState(),
            )
        }
    }

    fun setImportRule(rule: AnswerRule) {
        _sessionState.update { it.copy(importRule = rule) }
    }

    fun importFileUri(uri: Uri, onNeedKeyEditor: () -> Unit) {
        viewModelScope.launch {
            _sessionState.update { it.copy(isImporting = true, statusMessage = null) }
            val result = repository.importDocumentUri(uri, _sessionState.value.importRule)
            result.fold(
                onSuccess = { newBankId ->
                    activateNewlyImportedBank(
                        newBankId = newBankId,
                        successPrefix = "Sənəd yükləndi",
                        onNeedKeyEditor = onNeedKeyEditor,
                    )
                },
                onFailure = { error ->
                    _sessionState.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = error.message ?: "Fayl oxunarkən xəta baş verdi.",
                            isStatusError = true,
                        )
                    }
                },
            )
        }
    }

    fun importRawText(title: String, rawText: String, onNeedKeyEditor: () -> Unit) {
        viewModelScope.launch {
            _sessionState.update { it.copy(isImporting = true, statusMessage = null) }
            val result = repository.importRawText(rawText, title, _sessionState.value.importRule)
            result.fold(
                onSuccess = { newBankId ->
                    activateNewlyImportedBank(
                        newBankId = newBankId,
                        successPrefix = "Mətndən test yaradıldı",
                        onNeedKeyEditor = onNeedKeyEditor,
                    )
                },
                onFailure = { error ->
                    _sessionState.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = error.message ?: "Mətn analiz edilə bilmədi.",
                            isStatusError = true,
                        )
                    }
                },
            )
        }
    }

    private suspend fun activateNewlyImportedBank(
        newBankId: Long,
        successPrefix: String,
        onNeedKeyEditor: () -> Unit,
    ) {
        examTickerJob?.cancel()
        val loadedQuestions = repository.getQuestionsOnce(newBankId)
        val shuffleEnabled = prefs.shuffleEnabledFlow.first()
        val hasUnconfirmedKeys = loadedQuestions.any { !it.hasConfirmedKey }
        val builtItems = QuizEngine.buildSessionItems(loadedQuestions, shuffleEnabled)

        prefs.setActiveBankId(newBankId)
        _activeBankId.value = newBankId
        _sessionState.update {
            it.copy(
                builtForBankId = newBankId,
                builtForShuffle = shuffleEnabled,
                forceRebuild = false,
                isImporting = false,
                mode = StudyMode.QUIZ,
                selectedChunkIndex = null,
                items = builtItems,
                currentIndex = 0,
                flashcardIndex = 0,
                isFlashcardFlipped = false,
                isSessionFinished = false,
                correctCount = 0,
                incorrectCount = 0,
                streakCount = 0,
                exam = ActiveExamUiState(),
                statusMessage = if (hasUnconfirmedKeys) {
                    "$successPrefix (${loadedQuestions.size} sual). Doğru cavabları təsdiqləyin."
                } else {
                    "$successPrefix: ${loadedQuestions.size} sual açıldı."
                },
                isStatusError = false,
            )
        }
        if (hasUnconfirmedKeys) {
            onNeedKeyEditor()
        }
    }

    fun updateSingleAnswerKey(question: QuizQuestion, selectedCorrectText: String, reshuffleNow: Boolean) {
        if (question.rowId <= 0L) return
        viewModelScope.launch {
            repository.updateSingleAnswerKey(question.rowId, selectedCorrectText)
            if (reshuffleNow) {
                prefs.setShuffleEnabled(true)
                _sessionState.update {
                    it.copy(
                        forceRebuild = true,
                        statusMessage = "Doğru cavab yeniləndi və suallar qarışdırıldı.",
                        isStatusError = false,
                    )
                }
            }
        }
    }

    fun selectAllFirstOptions() {
        val bankId = _uiState.value.activeBank?.id ?: return
        viewModelScope.launch {
            repository.selectAllFirstOptions(bankId)
            setStatus("Bütün suallarda 1-ci variant doğru cavab seçildi.", isError = false)
        }
    }

    fun startQuizFromKeyEditor() {
        val bankId = _uiState.value.activeBank?.id ?: return
        viewModelScope.launch {
            prefs.setShuffleEnabled(true)
            repository.resetMastery(bankId)
            _sessionState.update {
                it.copy(
                    mode = StudyMode.QUIZ,
                    selectedChunkIndex = null,
                    forceRebuild = true,
                    streakCount = 0,
                    statusMessage = "Testlər və variantlar qarışdırıldı.",
                    isStatusError = false,
                )
            }
        }
    }

    fun switchBank(bankId: Long) {
        examTickerJob?.cancel()
        viewModelScope.launch {
            prefs.setActiveBankId(bankId)
            _activeBankId.value = bankId
            _sessionState.update {
                it.copy(
                    mode = StudyMode.QUIZ,
                    selectedChunkIndex = null,
                    forceRebuild = true,
                    streakCount = 0,
                    exam = ActiveExamUiState(),
                )
            }
        }
    }

    fun deleteBank(bank: QuizBank) {
        viewModelScope.launch {
            repository.deleteBank(bank.id)
            val remaining = repository.banksFlow.first()
            val nextId = remaining.firstOrNull { it.id != bank.id }?.id
            if (nextId != null) prefs.setActiveBankId(nextId) else prefs.clearActiveBankId()
            _activeBankId.value = nextId
            _sessionState.update {
                it.copy(
                    mode = StudyMode.QUIZ,
                    selectedChunkIndex = null,
                    forceRebuild = true,
                    streakCount = 0,
                    exam = ActiveExamUiState(),
                )
            }
        }
    }

    fun resetProgress() {
        val bankId = _uiState.value.activeBank?.id ?: return
        viewModelScope.launch {
            repository.resetMastery(bankId)
            _sessionState.update {
                it.copy(
                    forceRebuild = true,
                    streakCount = 0,
                    statusMessage = "İrəliləyiş və səhv siyahısı sıfırlandı.",
                    isStatusError = false,
                )
            }
        }
    }

    fun toggleTheme() {
        val next = if (_uiState.value.themeMode == TestBazaThemeMode.DARK) {
            TestBazaThemeMode.LIGHT
        } else {
            TestBazaThemeMode.DARK
        }
        viewModelScope.launch { prefs.setThemeMode(next) }
    }

    fun exportHtmlToUri(uri: Uri) {
        val state = _uiState.value
        val bankTitle = state.activeBank?.title ?: "Testbaza"
        val questions = state.rawQuestions
        if (questions.isEmpty()) {
            setStatus("İxrac etmək üçün sual bazası boşdur.", isError = true)
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val html = HtmlExporter.buildStandaloneHtml(bankTitle, questions)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(html.toByteArray(Charsets.UTF_8))
                    } ?: error("Fayl yazıla bilmədi.")
                }
            }
            result.fold(
                onSuccess = { setStatus("Hazır oflayn HTML faylı saxlanıldı.", isError = false) },
                onFailure = { err -> setStatus(err.message ?: "HTML saxlanarkən xəta baş verdi.", isError = true) },
            )
        }
    }

    fun suggestedExportFilename(): String =
        HtmlExporter.safeExportFileName(_uiState.value.activeBank?.title ?: "Testbaza")

    fun dismissStatusMessage() {
        _sessionState.update { it.copy(statusMessage = null) }
    }

    fun showStatus(message: String, isError: Boolean) {
        setStatus(message, isError)
    }

    private fun setStatus(message: String, isError: Boolean) {
        _sessionState.update {
            it.copy(statusMessage = message, isStatusError = isError)
        }
    }

    override fun onCleared() {
        super.onCleared()
        examTickerJob?.cancel()
    }

    private data class SessionSyncInput(
        val ready: Boolean,
        val activeId: Long?,
        val questions: List<QuizQuestion>,
        val shuffleEnabled: Boolean,
    )

    private data class AttemptStreamResult(
        val attempts: List<SharedExamAttempt>,
        val error: String?,
    )

    private data class CloudUiInternalState(
        val isCloudAvailable: Boolean = false,
        val uid: String? = null,
        val displayName: String? = null,
        val email: String? = null,
        val isBusy: Boolean = false,
        val mySharedExams: List<SharedExam> = emptyList(),
        val inspectedAttempts: List<SharedExamAttempt> = emptyList(),
        val attemptsErrorMessage: String? = null,
        val fetchedPreviewExam: SharedExam? = null,
        val latestPublishedExam: SharedExam? = null,
    )

    private data class SessionState(
        val builtForBankId: Long? = null,
        val builtForShuffle: Boolean? = null,
        val forceRebuild: Boolean = false,
        val isImporting: Boolean = false,
        val mode: StudyMode = StudyMode.QUIZ,
        val selectedChunkIndex: Int? = null,
        val items: List<SessionQuestionItem> = emptyList(),
        val currentIndex: Int = 0,
        val flashcardIndex: Int = 0,
        val isFlashcardFlipped: Boolean = false,
        val isSessionFinished: Boolean = false,
        val correctCount: Int = 0,
        val incorrectCount: Int = 0,
        val streakCount: Int = 0,
        val importRule: AnswerRule = AnswerRule.AUTO,
        val statusMessage: String? = null,
        val isStatusError: Boolean = false,
        val exam: ActiveExamUiState = ActiveExamUiState(),
    )

    private companion object {
        const val TAG = "TestBazaVM"
    }
}
