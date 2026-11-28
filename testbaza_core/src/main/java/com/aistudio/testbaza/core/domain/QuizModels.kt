package com.aistudio.testbaza.core.domain

enum class StudyMode(val label: String) {
    QUIZ("Hamısı"),
    FLASHCARD("Kartlar"),
    EXAM("Seçilmiş"),
    WEAK("Səhvlərim"),
}

enum class AnswerRule(val label: String, val description: String) {
    AUTO("Avtomatik", "(*, +, ✓ və ya Qalın yazı)"),
    FIRST("1-ci variant", "Hər sualın ilk variantı doğru sayılır"),
    BOLD("Yalnız Bold", "Qalın (Bold) yazılmış variant doğru sayılır"),
    MARKER("İşarəli (*, +, ✓)", "Simvolla işarələnmiş variant doğru sayılır"),
}

enum class QuestionMasteryStatus {
    NEW,
    MASTERED,
    WEAK,
}

enum class TestBazaThemeMode {
    DARK,
    LIGHT,
}

data class QuizBank(
    val id: Long,
    val title: String,
    val createdAtEpochMs: Long,
    val isDefault: Boolean,
    val questionCount: Int,
)

data class QuizQuestion(
    val rowId: Long,
    val bankId: Long,
    val questionNumber: Int,
    val category: String,
    val isExam: Boolean,
    val questionText: String,
    val allOptions: List<String>,
    val correctText: String,
    val hasConfirmedKey: Boolean,
    val masteryStatus: QuestionMasteryStatus,
    val wrongCount: Int,
    val correctStreak: Int,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val totalTimeSeconds: Long = 0L,
    val lastTimeSeconds: Long = 0L,
    val timedCount: Int = 0,
) {
    val averageTimeSeconds: Long
        get() = if (timedCount > 0) (totalTimeSeconds + timedCount / 2) / timedCount else lastTimeSeconds
}

data class SessionQuestionItem(
    val question: QuizQuestion,
    val shuffledOptions: List<String>,
    val correctIdx: Int,
    val disabledOptions: Set<Int> = emptySet(),
    val selectedIdx: Int? = null,
    val isCorrect: Boolean? = null,
) {
    val isAnswered: Boolean get() = selectedIdx != null
}

data class ChunkOption(
    val index: Int?,
    val label: String,
)

data class ExamConfig(
    val questionCount: Int = 20,
    val durationMinutes: Int = 20,
) {
    companion object {
        const val MIN_QUESTIONS = 1
        const val MAX_QUESTIONS = 150
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 180
    }
}

data class ExamWrongDetail(
    val questionNumber: Int = 0,
    val questionText: String = "",
    val selectedAnswer: String = "",
    val correctAnswer: String = "",
    val timeSpentSeconds: Long = 0L,
)

data class QuestionTimingDetail(
    val questionNumber: Int = 0,
    val questionText: String = "",
    val status: String = "SKIPPED",
    val timeSpentSeconds: Long = 0L,
)

data class ExamEvaluationSummary(
    val totalQuestions: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val wrongDetails: List<ExamWrongDetail>,
    val questionTimings: List<QuestionTimingDetail>,
)

data class ExamAttemptHistoryItem(
    val id: Long,
    val bankId: Long,
    val bankTitle: String,
    val createdAtEpochMs: Long,
    val isTimedExam: Boolean,
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val totalElapsedSeconds: Long,
    val durationLimitMinutes: Int,
    val timedOut: Boolean,
    val wrongDetails: List<ExamWrongDetail>,
    val questionTimings: List<QuestionTimingDetail>,
) {
    fun toEvaluationSummary(): ExamEvaluationSummary = ExamEvaluationSummary(
        totalQuestions = totalQuestions,
        answeredCount = correctCount + wrongCount,
        correctCount = correctCount,
        wrongCount = wrongCount,
        unansweredCount = unansweredCount,
        wrongDetails = wrongDetails,
        questionTimings = questionTimings,
    )
}

data class FileStatisticsSummary(
    val bankId: Long,
    val bankTitle: String,
    val totalQuestions: Int,
    val answeredQuestionsCount: Int,
    val masteredCount: Int,
    val weakCount: Int,
    val newCount: Int,
    val totalAttempts: Int,
    val totalCorrectAttempts: Int,
    val totalWrongAttempts: Int,
    val accuracyPercent: Int,
    val totalTimeSeconds: Long,
    val avgSecondsPerTimedQuestion: Long,
    val slowestQuestions: List<QuizQuestion>,
    val mostWrongQuestions: List<QuizQuestion>,
    val timedQuestions: List<QuizQuestion>,
    val estimated50QuestionSeconds: Long,
)

data class ActiveExamUiState(
    val active: Boolean = false,
    val title: String = "",
    val durationMinutes: Int = 20,
    val remainingSeconds: Int = 0,
    val currentQuestionElapsedSeconds: Long = 0L,
    val totalElapsedSeconds: Long = 0L,
    val timedOut: Boolean = false,
    val perQuestionSeconds: List<Long> = emptyList(),
    val evaluationSummary: ExamEvaluationSummary? = null,
)

data class ShareResultPayload(
    val title: String,
    val summary: ExamEvaluationSummary,
    val totalElapsedSeconds: Long,
    val durationLimitMinutes: Int? = null,
    val timedOut: Boolean = false,
)
