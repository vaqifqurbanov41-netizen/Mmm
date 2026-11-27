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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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

    private val _activeBankId = MutableStateFlow<Long?>(null)
    private val _sessionState = MutableStateFlow(SessionState())
    private val _uiState = MutableStateFlow(TestBazaUiState())
    val uiState: StateFlow<TestBazaUiState> = _uiState.asStateFlow()

    private val banksState = repository.banksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    private val questionsFlow = _activeBankId
        .filterNotNull()
        .flatMapLatest { bankId -> repository.observeQuestions(bankId) }

    init {
        viewModelScope.launch {
            val defaultBankId = repository.ensureDefaultBankSeeded()
            prefs.activeBankIdFlow.collect { savedId ->
                val targetId = savedId ?: defaultBankId
                if (_activeBankId.value != targetId) {
                    _activeBankId.value = targetId
                }
            }
        }

        viewModelScope.launch {
            combine(
                banksState,
                _activeBankId,
                questionsFlow,
                prefs.themeModeFlow,
                prefs.shuffleEnabledFlow,
            ) { banks, activeId, questions, theme, shuffleEnabled ->
                ObservedBundle(banks, activeId, questions, theme, shuffleEnabled)
            }.combine(_sessionState) { bundle, session ->
                val resolvedBank = bundle.banks.firstOrNull { it.id == bundle.activeId }
                    ?: bundle.banks.firstOrNull()

                if (resolvedBank != null && resolvedBank.id != bundle.activeId) {
                    _activeBankId.value = resolvedBank.id
                }

                val mergedSession = syncSessionWithQuestions(
                    previous = session,
                    questions = bundle.questions,
                    shuffleEnabled = bundle.shuffleEnabled,
                )

                TestBazaUiState(
                    isLoading = false,
                    isImporting = mergedSession.isImporting,
                    themeMode = bundle.theme,
                    banks = bundle.banks,
                    activeBank = resolvedBank,
                    rawQuestions = bundle.questions,
                    mode = mergedSession.mode,
                    selectedChunkIndex = mergedSession.selectedChunkIndex,
                    chunkOptions = QuizEngine.buildChunkOptions(bundle.questions.size),
                    isShuffled = bundle.shuffleEnabled,
                    sessionItems = mergedSession.items,
                    currentIndex = mergedSession.currentIndex,
                    flashcardIndex = mergedSession.flashcardIndex,
                    isFlashcardFlipped = mergedSession.isFlashcardFlipped,
                    isSessionFinished = mergedSession.isSessionFinished,
                    correctCount = mergedSession.correctCount,
                    incorrectCount = mergedSession.incorrectCount,
                    streakCount = mergedSession.streakCount,
                    importRule = mergedSession.importRule,
                    statusMessage = mergedSession.statusMessage,
                    isStatusError = mergedSession.isStatusError,
                )
            }.collect { nextState ->
                _uiState.value = nextState
            }
        }
    }

    private fun syncSessionWithQuestions(
        previous: SessionState,
        questions: List<QuizQuestion>,
        shuffleEnabled: Boolean,
    ): SessionState {
        val activeBankId = questions.firstOrNull()?.bankId
        val shouldRebuild = previous.builtForBankId != activeBankId ||
            previous.builtForShuffle != shuffleEnabled ||
            previous.forceRebuild

        if (!shouldRebuild) {
            val qMap = questions.associateBy { it.rowId }
            val updatedItems = previous.items.mapNotNull { item ->
                val updatedQ = qMap[item.question.rowId] ?: return@mapNotNull null
                item.copy(question = updatedQ)
            }
            return previous.copy(
                items = updatedItems,
                currentIndex = previous.currentIndex.coerceIn(0, maxOf(0, updatedItems.lastIndex)),
                flashcardIndex = previous.flashcardIndex.coerceIn(0, maxOf(0, updatedItems.lastIndex)),
            )
        }

        val filtered = QuizEngine.filterBaseQuestions(
            allQuestions = questions,
            mode = previous.mode,
            selectedChunkIndex = previous.selectedChunkIndex,
        )
        val builtItems = QuizEngine.buildSessionItems(
            questions = filtered,
            shuffle = shuffleEnabled,
        )
        val updatedState = previous.copy(
            builtForBankId = activeBankId,
            builtForShuffle = shuffleEnabled,
            forceRebuild = false,
            items = builtItems,
            currentIndex = 0,
            flashcardIndex = 0,
            isFlashcardFlipped = false,
            isSessionFinished = false,
            correctCount = 0,
            incorrectCount = 0,
        )
        _sessionState.value = updatedState
        return updatedState
    }

    fun switchMode(newMode: StudyMode) {
        val current = _uiState.value
        if (newMode == StudyMode.EXAM && current.examCount == 0) {
            setStatus("Hələ seçilmiş (ulduzlu) sual yoxdur. Sual kartındakı 'Seç' düyməsi ilə əlavə edin.", isError = true)
            return
        }
        if (newMode == StudyMode.WEAK && current.weakCount == 0) {
            setStatus("Hazırda təkrar olunacaq səhv cavab yoxdur.", isError = false)
            return
        }
        _sessionState.update {
            it.copy(
                mode = newMode,
                selectedChunkIndex = if (newMode == StudyMode.EXAM || newMode == StudyMode.WEAK) null else it.selectedChunkIndex,
                forceRebuild = true,
            )
        }
    }

    fun selectChunk(chunkIndex: Int?) {
        _sessionState.update {
            val nextMode = if (it.mode == StudyMode.EXAM || it.mode == StudyMode.WEAK) StudyMode.QUIZ else it.mode
            it.copy(
                mode = nextMode,
                selectedChunkIndex = chunkIndex,
                forceRebuild = true,
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
        _sessionState.update { it.copy(forceRebuild = true, isSessionFinished = false) }
    }

    fun returnToFullQuiz() {
        _sessionState.update {
            it.copy(
                mode = StudyMode.QUIZ,
                selectedChunkIndex = null,
                forceRebuild = true,
                isSessionFinished = false,
            )
        }
    }

    fun setImportRule(rule: AnswerRule) {
        _sessionState.update { it.copy(importRule = rule) }
    }

    fun importFileUri(uri: Uri, onOpenKeyEditor: () -> Unit) {
        viewModelScope.launch {
            _sessionState.update { it.copy(isImporting = true, statusMessage = null) }
            val result = repository.importDocumentUri(uri, _sessionState.value.importRule)
            result.fold(
                onSuccess = { newBankId ->
                    prefs.setActiveBankId(newBankId)
                    _activeBankId.value = newBankId
                    _sessionState.update {
                        it.copy(
                            isImporting = false,
                            mode = StudyMode.QUIZ,
                            selectedChunkIndex = null,
                            forceRebuild = true,
                            statusMessage = "Yeni test bazası uğurla yükləndi.",
                            isStatusError = false,
                        )
                    }
                    onOpenKeyEditor()
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

    fun importRawText(title: String, rawText: String, onSuccessOpenKeyEditor: () -> Unit) {
        viewModelScope.launch {
            _sessionState.update { it.copy(isImporting = true, statusMessage = null) }
            val result = repository.importRawText(rawText, title, _sessionState.value.importRule)
            result.fold(
                onSuccess = { newBankId ->
                    prefs.setActiveBankId(newBankId)
                    _activeBankId.value = newBankId
                    _sessionState.update {
                        it.copy(
                            isImporting = false,
                            mode = StudyMode.QUIZ,
                            selectedChunkIndex = null,
                            forceRebuild = true,
                            statusMessage = "Mətndən yeni test bazası yaradıldı.",
                            isStatusError = false,
                        )
                    }
                    onSuccessOpenKeyEditor()
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
        if (bank.isDefault) return
        viewModelScope.launch {
            repository.deleteBank(bank.id)
            val fallback = banksState.value.firstOrNull { it.id != bank.id }?.id
                ?: repository.ensureDefaultBankSeeded()
            prefs.setActiveBankId(fallback)
            _activeBankId.value = fallback
            _sessionState.update { it.copy(forceRebuild = true) }
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
        val bankTitle = state.activeBank?.title ?: "TestBaza"
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
        HtmlExporter.safeExportFileName(_uiState.value.activeBank?.title ?: "TestBaza")

    fun dismissStatusMessage() {
        _sessionState.update { it.copy(statusMessage = null) }
    }

    private fun setStatus(message: String, isError: Boolean) {
        _sessionState.update {
            it.copy(statusMessage = message, isStatusError = isError)
        }
    }

    private data class ObservedBundle(
        val banks: List<QuizBank>,
        val activeId: Long?,
        val questions: List<QuizQuestion>,
        val theme: TestBazaThemeMode,
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
