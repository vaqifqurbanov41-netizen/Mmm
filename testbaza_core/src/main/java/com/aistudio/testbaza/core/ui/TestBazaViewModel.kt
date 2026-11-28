package com.aistudio.testbaza.core.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.testbaza.core.data.QuizRepository
import com.aistudio.testbaza.core.data.local.QuizDatabase
import com.aistudio.testbaza.core.data.local.QuizPreferencesStore
import com.aistudio.testbaza.core.domain.ActiveExamUiState
import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.ChunkOption
import com.aistudio.testbaza.core.domain.ExamAttemptHistoryItem
import com.aistudio.testbaza.core.domain.ExamConfig
import com.aistudio.testbaza.core.domain.ExamEvaluationSummary
import com.aistudio.testbaza.core.domain.FileStatisticsSummary
import com.aistudio.testbaza.core.domain.HtmlExporter
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.QuizQuestion
import com.aistudio.testbaza.core.domain.SessionQuestionItem
import com.aistudio.testbaza.core.domain.ShareResultPayload
import com.aistudio.testbaza.core.domain.StudyMode
import com.aistudio.testbaza.core.domain.TestBazaThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val practiceSummary: ExamEvaluationSummary? = null,
    val practiceElapsedSeconds: Long = 0L,
    val activeBankStatistics: FileStatisticsSummary? = null,
    val selectedStatsBankId: Long? = null,
    val selectedBankStatistics: FileStatisticsSummary? = null,
    val selectedBankExamHistory: List<ExamAttemptHistoryItem> = emptyList(),
) {
    val totalCount: Int get() = rawQuestions.size
    val examCount: Int get() = rawQuestions.count { it.isExam }
    val weakCount: Int get() = rawQuestions.count { it.masteryStatus == QuestionMasteryStatus.WEAK }
    val keyedCount: Int get() = rawQuestions.count { it.hasConfirmedKey }
    val currentSessionItem: SessionQuestionItem? get() = sessionItems.getOrNull(currentIndex)
    val currentFlashcardItem: SessionQuestionItem? get() = sessionItems.getOrNull(flashcardIndex)
    val isExamActive: Boolean get() = activeExam.active
    val latestSharePayload: ShareResultPayload?
        get() = when {
            isExamActive && activeExam.evaluationSummary != null -> ShareResultPayload(
                title = activeExam.title.ifBlank { activeBank?.title ?: "Sınaq İmtahanı" },
                summary = activeExam.evaluationSummary,
                totalElapsedSeconds = activeExam.totalElapsedSeconds,
                durationLimitMinutes = activeExam.durationMinutes,
                timedOut = activeExam.timedOut,
            )
            isSessionFinished && practiceSummary != null -> ShareResultPayload(
                title = activeBank?.title ?: "Testbaza",
                summary = practiceSummary,
                totalElapsedSeconds = practiceElapsedSeconds,
                durationLimitMinutes = null,
                timedOut = false,
            )
            else -> null
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestBazaViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val database = QuizDatabase.getInstance(application)
    private val repository = QuizRepository(application, database.quizDao())
    private val prefs = QuizPreferencesStore(application)

    private val _initialized = MutableStateFlow(false)
    private val _activeBankId = MutableStateFlow<Long?>(null)
    private val _selectedStatsBankId = MutableStateFlow<Long?>(null)
    private val _sessionState = MutableStateFlow(SessionState())
    private val _uiState = MutableStateFlow(TestBazaUiState())
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

    private val effectiveStatsBankIdFlow = combine(_selectedStatsBankId, _activeBankId, banksState) { selectedId, activeId, banks ->
        val validSelected = selectedId?.takeIf { id -> banks.any { it.id == id } }
        validSelected ?: activeId ?: banks.firstOrNull()?.id
    }

    private val statsQuestionsFlow = effectiveStatsBankIdFlow.flatMapLatest { bankId ->
        if (bankId == null) flowOf(emptyList())
        else repository.observeQuestions(bankId)
    }

    private val statsHistoryFlow = effectiveStatsBankIdFlow.flatMapLatest { bankId ->
        if (bankId == null) flowOf(emptyList())
        else repository.observeExamHistory(bankId)
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
            _selectedStatsBankId.value = initialId
            _initialized.value = true
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
                    resetPerQuestionClocks(0)
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
                            practiceSummary = null,
                            practiceElapsedSeconds = 0L,
                        )
                    }
                    return@collect
                }
                if (input.questions.isNotEmpty() && input.questions.first().bankId != input.activeId) {
                    return@collect
                }

                var rebuiltSize: Int? = null
                _sessionState.update { prev ->
                    val shouldRebuild = prev.builtForBankId != input.activeId ||
                        prev.builtForShuffle != input.shuffleEnabled ||
                        prev.forceRebuild
                    if (!shouldRebuild) {
                        val qMap = input.questions.associateBy { it.rowId }
                        val updatedItems = prev.items.mapNotNull { item ->
                            val updatedQ = qMap[item.question.rowId] ?: return@mapNotNull null
                            val optionsOrKeyChanged =
                                updatedQ.allOptions != item.question.allOptions ||
                                    updatedQ.correctText != item.question.correctText
                            if (optionsOrKeyChanged) {
                                QuizEngine.buildSessionItems(listOf(updatedQ), input.shuffleEnabled)
                                    .firstOrNull()
                                    ?: item.copy(question = updatedQ)
                            } else {
                                item.copy(question = updatedQ)
                            }
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
                        rebuiltSize = builtItems.size
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
                            practiceSummary = null,
                            practiceElapsedSeconds = 0L,
                        )
                    }
                }
                rebuiltSize?.let { resetPerQuestionClocks(it) }
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
                _sessionState,
                effectiveStatsBankIdFlow,
                statsQuestionsFlow,
                statsHistoryFlow,
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
                val session = values[7] as SessionState
                val statsBankId = values[8] as Long?
                @Suppress("UNCHECKED_CAST")
                val statsQuestions = values[9] as List<QuizQuestion>
                @Suppress("UNCHECKED_CAST")
                val statsHistory = values[10] as List<ExamAttemptHistoryItem>

                val resolvedBank = if (activeId != null) banks.firstOrNull { it.id == activeId } else null
                val validQuestions = if (activeId == null) emptyList() else rawQuestions.filter { it.bankId == activeId }

                val activeStats = if (resolvedBank != null) {
                    QuizEngine.computeFileStatistics(
                        bankId = resolvedBank.id,
                        bankTitle = resolvedBank.title,
                        questions = validQuestions,
                    )
                } else null

                val resolvedStatsBank = if (statsBankId != null) banks.firstOrNull { it.id == statsBankId } else null
                val validStatsQuestions = if (statsBankId == null) emptyList() else statsQuestions.filter { it.bankId == statsBankId }
                val selectedStats = if (resolvedStatsBank != null) {
                    QuizEngine.computeFileStatistics(
                        bankId = resolvedStatsBank.id,
                        bankTitle = resolvedStatsBank.title,
                        questions = validStatsQuestions,
                    )
                } else null

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
                    practiceSummary = session.practiceSummary,
                    practiceElapsedSeconds = session.practiceElapsedSeconds,
                    activeBankStatistics = activeStats,
                    selectedStatsBankId = statsBankId,
                    selectedBankStatistics = selectedStats,
                    selectedBankExamHistory = statsHistory,
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

    // --- Local Timed Exam Engine ---

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
        examTickerJob?.cancel()
        resetPerQuestionClocks(builtItems.size)
        val durationMinutes = current.examConfig.durationMinutes.coerceIn(
            ExamConfig.MIN_DURATION_MINUTES,
            ExamConfig.MAX_DURATION_MINUTES,
        )
        val limitSeconds = durationMinutes * 60

        _sessionState.update {
            it.copy(
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
                practiceSummary = null,
                practiceElapsedSeconds = 0L,
                exam = ActiveExamUiState(
                    active = true,
                    title = current.activeBank?.title ?: "Sınaq İmtahanı",
                    durationMinutes = durationMinutes,
                    remainingSeconds = limitSeconds,
                    currentQuestionElapsedSeconds = 0L,
                    totalElapsedSeconds = 0L,
                    timedOut = false,
                    perQuestionSeconds = List(builtItems.size) { 0L },
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

    private fun resetPerQuestionClocks(itemCount: Int) {
        perQuestionElapsedMs = LongArray(itemCount.coerceAtLeast(0)) { 0L }
        activeQuestionStartedAtMs = if (itemCount > 0) SystemClock.elapsedRealtime() else 0L
    }

    private fun flushCurrentQuestionElapsedTime() {
        val session = _sessionState.value
        if (session.isSessionFinished || session.items.isEmpty()) return
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

    private fun buildCurrentPerQuestionSeconds(): List<Long> =
        perQuestionElapsedMs.map { ms ->
            if (ms <= 0L) 0L else maxOf(1L, (ms + 500L) / 1000L)
        }

    fun finishActiveExam(timedOut: Boolean = false) {
        val session = _sessionState.value
        if (!session.exam.active || session.isSessionFinished) return
        flushCurrentQuestionElapsedTime()
        examTickerJob?.cancel()
        examTickerJob = null

        val perQuestionSeconds = buildCurrentPerQuestionSeconds()
        val summary = QuizEngine.buildExamEvaluationSummary(session.items, perQuestionSeconds)
        val totalElapsed = maxOf(session.exam.totalElapsedSeconds, perQuestionSeconds.sum())
        val bankId = _uiState.value.activeBank?.id
        val bankTitle = session.exam.title.ifBlank { _uiState.value.activeBank?.title ?: "Sınaq İmtahanı" }

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

        viewModelScope.launch {
            repository.recordBatchQuestionTimings(session.items, perQuestionSeconds)
            if (bankId != null) {
                repository.saveExamHistoryRecord(
                    bankId = bankId,
                    bankTitle = bankTitle,
                    isTimedExam = true,
                    summary = summary,
                    totalElapsedSeconds = totalElapsed,
                    durationLimitMinutes = session.exam.durationMinutes,
                    timedOut = timedOut,
                )
            }
        }
    }

    fun exitExamMode() {
        examTickerJob?.cancel()
        examTickerJob = null
        returnToFullQuiz()
    }

    // --- Statistics Actions ---

    fun selectStatisticsBank(bankId: Long) {
        _selectedStatsBankId.value = bankId
    }

    fun clearBankStatistics(bankId: Long) {
        viewModelScope.launch {
            repository.clearBankStatistics(bankId)
            val currentActive = _activeBankId.value
            if (currentActive == bankId && !_sessionState.value.exam.active) {
                _sessionState.update {
                    it.copy(
                        forceRebuild = true,
                        streakCount = 0,
                        statusMessage = "Fayl üzrə statistika və səhvlər təmizləndi.",
                        isStatusError = false,
                    )
                }
            } else {
                setStatus("Fayl üzrə statistika və səhvlər təmizləndi.", isError = false)
            }
        }
    }

    fun practiceWeakQuestionsForBank(bankId: Long) {
        viewModelScope.launch {
            val targetBankId = bankId
            val questions = if (targetBankId == _activeBankId.value) {
                _uiState.value.rawQuestions
            } else {
                repository.getQuestionsOnce(targetBankId)
            }
            val weakQuestions = questions.filter { it.masteryStatus == QuestionMasteryStatus.WEAK }
            if (weakQuestions.isEmpty()) {
                setStatus("Bu faylda təkrar olunacaq səhv sual yoxdur.", isError = false)
                return@launch
            }
            examTickerJob?.cancel()
            examTickerJob = null
            if (targetBankId != _activeBankId.value) {
                prefs.setActiveBankId(targetBankId)
                _activeBankId.value = targetBankId
            }
            val shuffle = _uiState.value.isShuffled
            val builtItems = QuizEngine.buildSessionItems(weakQuestions, shuffle)
            resetPerQuestionClocks(builtItems.size)
            _sessionState.update {
                it.copy(
                    mode = StudyMode.WEAK,
                    selectedChunkIndex = null,
                    builtForBankId = targetBankId,
                    builtForShuffle = shuffle,
                    forceRebuild = false,
                    items = builtItems,
                    currentIndex = 0,
                    flashcardIndex = 0,
                    isFlashcardFlipped = false,
                    isSessionFinished = false,
                    correctCount = 0,
                    incorrectCount = 0,
                    practiceSummary = null,
                    practiceElapsedSeconds = 0L,
                    exam = ActiveExamUiState(),
                )
            }
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
        val nextChunk = if (newMode == StudyMode.EXAM || newMode == StudyMode.WEAK) null else _sessionState.value.selectedChunkIndex
        val filtered = QuizEngine.filterBaseQuestions(
            allQuestions = current.rawQuestions,
            mode = newMode,
            selectedChunkIndex = nextChunk,
        )
        val builtItems = QuizEngine.buildSessionItems(filtered, current.isShuffled)
        resetPerQuestionClocks(builtItems.size)
        _sessionState.update { state ->
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
                practiceSummary = null,
                practiceElapsedSeconds = 0L,
                exam = ActiveExamUiState(),
            )
        }
    }

    fun selectChunk(chunkIndex: Int?) {
        if (_sessionState.value.exam.active) return
        val current = _uiState.value
        val nextMode = if (_sessionState.value.mode == StudyMode.EXAM || _sessionState.value.mode == StudyMode.WEAK) {
            StudyMode.QUIZ
        } else {
            _sessionState.value.mode
        }
        val filtered = QuizEngine.filterBaseQuestions(
            allQuestions = current.rawQuestions,
            mode = nextMode,
            selectedChunkIndex = chunkIndex,
        )
        val builtItems = QuizEngine.buildSessionItems(filtered, current.isShuffled)
        resetPerQuestionClocks(builtItems.size)
        _sessionState.update { state ->
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
                practiceSummary = null,
                practiceElapsedSeconds = 0L,
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

        var practiceSpentSeconds: Long? = null
        if (!currentSession.exam.active) {
            flushCurrentQuestionElapsedTime()
            val ms = perQuestionElapsedMs.getOrElse(currentSession.currentIndex) { 0L }
            practiceSpentSeconds = if (ms <= 0L) 1L else maxOf(1L, (ms + 500L) / 1000L)
        }

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

        if (item.question.rowId > 0L) {
            viewModelScope.launch {
                repository.recordAnswerOutcome(
                    question = item.question,
                    isCorrect = isCorrect,
                    spentSeconds = practiceSpentSeconds,
                )
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
            if (current.currentIndex < current.items.lastIndex) {
                flushCurrentQuestionElapsedTime()
                val nextIdx = current.currentIndex + 1
                _sessionState.update { it.copy(currentIndex = nextIdx) }
                resetQuestionClockForIndex(nextIdx)
            } else {
                finishPracticeSession()
            }
        }
    }

    private fun finishPracticeSession() {
        val session = _sessionState.value
        if (session.isSessionFinished) return
        flushCurrentQuestionElapsedTime()
        val perQuestionSeconds = buildCurrentPerQuestionSeconds()
        val summary = QuizEngine.buildExamEvaluationSummary(session.items, perQuestionSeconds)
        val totalElapsed = perQuestionSeconds.sum()
        val bank = _uiState.value.activeBank

        _sessionState.update {
            it.copy(
                isSessionFinished = true,
                practiceSummary = summary,
                practiceElapsedSeconds = totalElapsed,
            )
        }

        if (bank != null && summary.answeredCount > 0) {
            viewModelScope.launch {
                repository.saveExamHistoryRecord(
                    bankId = bank.id,
                    bankTitle = bank.title,
                    isTimedExam = false,
                    summary = summary,
                    totalElapsedSeconds = totalElapsed,
                    durationLimitMinutes = 0,
                    timedOut = false,
                )
            }
        }
    }

    fun prevQuestion() {
        val current = _sessionState.value
        if (current.currentIndex <= 0) return
        flushCurrentQuestionElapsedTime()
        val prevIdx = current.currentIndex - 1
        _sessionState.update { it.copy(currentIndex = prevIdx) }
        resetQuestionClockForIndex(prevIdx)
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
            flushCurrentQuestionElapsedTime()
            _sessionState.update { it.copy(currentIndex = clamped, isSessionFinished = false) }
            resetQuestionClockForIndex(clamped)
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
            startLocalTimedExam()
            return
        }
        val current = _uiState.value
        val filtered = QuizEngine.filterBaseQuestions(
            allQuestions = current.rawQuestions,
            mode = currentState.mode,
            selectedChunkIndex = currentState.selectedChunkIndex,
        )
        val builtItems = QuizEngine.buildSessionItems(filtered, current.isShuffled)
        resetPerQuestionClocks(builtItems.size)
        _sessionState.update { state ->
            state.copy(
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
                practiceSummary = null,
                practiceElapsedSeconds = 0L,
            )
        }
    }

    fun returnToFullQuiz() {
        examTickerJob?.cancel()
        examTickerJob = null
        val current = _uiState.value
        val builtItems = QuizEngine.buildSessionItems(current.rawQuestions, current.isShuffled)
        resetPerQuestionClocks(builtItems.size)
        _sessionState.update { state ->
            state.copy(
                mode = StudyMode.QUIZ,
                selectedChunkIndex = null,
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
                practiceSummary = null,
                practiceElapsedSeconds = 0L,
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
        _selectedStatsBankId.value = newBankId
        resetPerQuestionClocks(builtItems.size)
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
                practiceSummary = null,
                practiceElapsedSeconds = 0L,
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

    fun updateQuestionContent(
        question: QuizQuestion,
        newQuestionText: String,
        newOptions: List<String>,
        newCorrectText: String,
        reshuffleNow: Boolean,
    ) {
        if (question.rowId <= 0L) return
        val cleanedQuestion = newQuestionText.trim()
        val cleanedOptions = newOptions.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanedQuestion.isEmpty() || cleanedOptions.size < 2) {
            setStatus("Sual mətni və ən azı 2 fərqli variant daxil edilməlidir.", isError = true)
            return
        }
        val resolvedCorrect = if (newCorrectText.trim() in cleanedOptions) {
            newCorrectText.trim()
        } else {
            cleanedOptions.first()
        }
        viewModelScope.launch {
            repository.updateQuestionContent(
                rowId = question.rowId,
                questionText = cleanedQuestion,
                allOptions = cleanedOptions,
                correctText = resolvedCorrect,
                hasConfirmedKey = true,
            )
            if (reshuffleNow) {
                prefs.setShuffleEnabled(true)
                _sessionState.update {
                    it.copy(
                        forceRebuild = true,
                        statusMessage = "Sual #${question.questionNumber} redaktə olundu və qarışdırıldı.",
                        isStatusError = false,
                    )
                }
            } else {
                setStatus("Sual #${question.questionNumber} redaktə olundu.", isError = false)
            }
        }
    }

    fun deleteSingleQuestion(question: QuizQuestion) {
        if (question.rowId <= 0L) return
        viewModelScope.launch {
            repository.deleteQuestion(question.rowId)
            setStatus("Sual #${question.questionNumber} bazadan silindi.", isError = false)
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
            _selectedStatsBankId.value = bankId
            _sessionState.update {
                it.copy(
                    mode = StudyMode.QUIZ,
                    selectedChunkIndex = null,
                    forceRebuild = true,
                    streakCount = 0,
                    practiceSummary = null,
                    practiceElapsedSeconds = 0L,
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
            if (_selectedStatsBankId.value == bank.id) {
                _selectedStatsBankId.value = nextId
            }
            _sessionState.update {
                it.copy(
                    mode = StudyMode.QUIZ,
                    selectedChunkIndex = null,
                    forceRebuild = true,
                    streakCount = 0,
                    practiceSummary = null,
                    practiceElapsedSeconds = 0L,
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
        val practiceSummary: ExamEvaluationSummary? = null,
        val practiceElapsedSeconds: Long = 0L,
        val exam: ActiveExamUiState = ActiveExamUiState(),
    )
}
