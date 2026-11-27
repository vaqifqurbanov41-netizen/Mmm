package com.aistudio.testbaza.core.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.testbaza.core.data.QuizRepository
import com.aistudio.testbaza.core.data.local.QuizDatabase
import com.aistudio.testbaza.core.data.local.QuizPreferencesStore
import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.ChunkOption
import com.aistudio.testbaza.core.domain.HtmlExporter
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizEngine
import com.aistudio.testbaza.core.domain.QuizQuestion
import com.aistudio.testbaza.core.domain.SessionQuestionItem
import com.aistudio.testbaza.core.domain.StudyMode
import com.aistudio.testbaza.core.domain.TestBazaThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
) {
    val totalCount: Int get() = rawQuestions.size
    val examCount: Int get() = rawQuestions.count { it.isExam }
    val weakCount: Int get() = rawQuestions.count { it.masteryStatus == QuestionMasteryStatus.WEAK }
    val keyedCount: Int get() = rawQuestions.count { it.hasConfirmedKey }
    val currentSessionItem: SessionQuestionItem? get() = sessionItems.getOrNull(currentIndex)
    val currentFlashcardItem: SessionQuestionItem? get() = sessionItems.getOrNull(flashcardIndex)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestBazaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = QuizDatabase.getInstance(application)
    private val repository = QuizRepository(application, database.quizDao())
    private val prefs = QuizPreferencesStore(application)

    private val _initialized = MutableStateFlow(false)
    private val _activeBankId = MutableStateFlow<Long?>(null)
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

    init {
        viewModelScope.launch {
            repository.removeBundledDefaultBank()
            val currentBanks = repository.banksFlow.first()
            val savedId = prefs.activeBankIdFlow.first()
            val validSaved = currentBanks.firstOrNull { it.id == savedId }?.id
            val initialId = validSaved ?: currentBanks.firstOrNull()?.id
            if (initialId != null) {
                prefs.setActiveBankId(initialId)
            } else {
                prefs.clearActiveBankId()
            }
            _activeBankId.value = initialId
            _initialized.value = true
        }

        // Rebuild or refresh session items whenever bank, questions, shuffle, or filter config changes
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
                // Skip stale emissions from previous bank during flatMapLatest switch
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
                        val builtItems = QuizEngine.buildSessionItems(
                            questions = filtered,
                            shuffle = input.shuffleEnabled,
                        )
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

        // Pure UI state projection (never mutates _sessionState or _activeBankId)
        viewModelScope.launch {
            combine(
                _initialized,
                banksState,
                _activeBankId,
                questionsFlow,
                prefs.themeModeFlow,
                prefs.shuffleEnabledFlow,
                _sessionState,
            ) { values ->
                val ready = values[0] as Boolean
                @Suppress("UNCHECKED_CAST")
                val banks = values[1] as List<QuizBank>
                val activeId = values[2] as Long?
                @Suppress("UNCHECKED_CAST")
                val rawQuestions = values[3] as List<QuizQuestion>
                val theme = values[4] as TestBazaThemeMode
                val shuffleEnabled = values[5] as Boolean
                val session = values[6] as SessionState

                val resolvedBank = if (activeId != null) {
                    banks.firstOrNull { it.id == activeId }
                } else {
                    null
                }
                val validQuestions = if (activeId == null) {
                    emptyList()
                } else {
                    rawQuestions.filter { it.bankId == activeId }
                }

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
                )
            }.collect { nextState ->
                _uiState.value = nextState
            }
        }
    }

    fun switchMode(newMode: StudyMode) {
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
            )
        }
    }

    fun selectChunk(chunkIndex: Int?) {
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

        viewModelScope.launch {
            repository.recordAnswerOutcome(item.question, isCorrect)
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
        viewModelScope.launch {
            repository.markMastered(item.question)
            setStatus("Sual #${item.question.questionNumber} mənimsənilib kimi qeyd olundu.", isError = false)
        }
    }

    fun toggleCurrentStar() {
        val item = _uiState.value.currentSessionItem ?: return
        viewModelScope.launch {
            repository.toggleStar(item.question)
        }
    }

    fun nextQuestion() {
        _sessionState.update { state ->
            if (state.items.isEmpty()) state
            else if (state.currentIndex < state.items.lastIndex) {
                state.copy(currentIndex = state.currentIndex + 1)
            } else {
                state.copy(isSessionFinished = true)
            }
        }
    }

    fun prevQuestion() {
        _sessionState.update { state ->
            if (state.currentIndex > 0) state.copy(currentIndex = state.currentIndex - 1) else state
        }
    }

    fun jumpToQuestionIndex(index: Int) {
        _sessionState.update { state ->
            val clamped = index.coerceIn(0, maxOf(0, state.items.lastIndex))
            if (state.mode == StudyMode.FLASHCARD) {
                state.copy(flashcardIndex = clamped, isFlashcardFlipped = false)
            } else {
                state.copy(currentIndex = clamped, isSessionFinished = false)
            }
        }
    }

    fun flipFlashcard() {
        _sessionState.update { it.copy(isFlashcardFlipped = !it.isFlashcardFlipped) }
    }

    fun nextFlashcard() {
        _sessionState.update { state ->
            if (state.flashcardIndex < state.items.lastIndex) {
                state.copy(flashcardIndex = state.flashcardIndex + 1, isFlashcardFlipped = false)
            } else {
                state
            }
        }
    }

    fun prevFlashcard() {
        _sessionState.update { state ->
            if (state.flashcardIndex > 0) {
                state.copy(flashcardIndex = state.flashcardIndex - 1, isFlashcardFlipped = false)
            } else {
                state
            }
        }
    }

    fun restartSession() {
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
        viewModelScope.launch {
            prefs.setActiveBankId(bankId)
            _activeBankId.value = bankId
            _sessionState.update {
                it.copy(
                    mode = StudyMode.QUIZ,
                    selectedChunkIndex = null,
                    forceRebuild = true,
                    streakCount = 0,
                )
            }
        }
    }

    fun deleteBank(bank: QuizBank) {
        viewModelScope.launch {
            repository.deleteBank(bank.id)
            val remaining = repository.banksFlow.first()
            val nextId = remaining.firstOrNull { it.id != bank.id }?.id
            if (nextId != null) {
                prefs.setActiveBankId(nextId)
            } else {
                prefs.clearActiveBankId()
            }
            _activeBankId.value = nextId
            _sessionState.update {
                it.copy(
                    mode = StudyMode.QUIZ,
                    selectedChunkIndex = null,
                    forceRebuild = true,
                    streakCount = 0,
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
        viewModelScope.launch {
            prefs.setThemeMode(next)
        }
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

    private fun setStatus(message: String, isError: Boolean) {
        _sessionState.update {
            it.copy(statusMessage = message, isStatusError = isError)
        }
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
    )
}
