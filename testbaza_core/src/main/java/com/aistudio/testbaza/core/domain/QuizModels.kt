package com.aistudio.testbaza.core.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

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
)

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

@Keep
@IgnoreExtraProperties
data class SharedExamQuestionSnapshot(
    val qNum: Long = 0L,
    val text: String = "",
    val options: List<String> = emptyList(),
    val correct: String = "",
)

@Keep
@IgnoreExtraProperties
data class SharedExam(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val title: String = "",
    val sourceBankTitle: String = "",
    val questionCount: Long = 0L,
    val durationMinutes: Long = 0L,
    val shuffleOptions: Boolean = true,
    val questions: List<SharedExamQuestionSnapshot> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ExamWrongDetail(
    val questionNumber: Long = 0L,
    val questionText: String = "",
    val selectedAnswer: String = "",
    val correctAnswer: String = "",
    val timeSpentSeconds: Long = 0L,
)

@Keep
@IgnoreExtraProperties
data class QuestionTimingDetail(
    val questionNumber: Long = 0L,
    val questionText: String = "",
    val status: String = "SKIPPED",
    val timeSpentSeconds: Long = 0L,
)

@Keep
@IgnoreExtraProperties
data class SharedExamAttempt(
    @DocumentId val id: String = "",
    val examId: String = "",
    val examOwnerId: String = "",
    val participantId: String = "",
    val participantName: String = "",
    val shareConsentGranted: Boolean = false,
    val totalQuestions: Long = 0L,
    val answeredCount: Long = 0L,
    val correctCount: Long = 0L,
    val wrongCount: Long = 0L,
    val unansweredCount: Long = 0L,
    val totalElapsedSeconds: Long = 0L,
    val durationLimitSeconds: Long = 0L,
    val timedOut: Boolean = false,
    val wrongDetails: List<ExamWrongDetail> = emptyList(),
    val questionTimings: List<QuestionTimingDetail> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
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

data class ActiveExamUiState(
    val active: Boolean = false,
    val title: String = "",
    val examCode: String? = null,
    val durationMinutes: Int = 20,
    val remainingSeconds: Int = 0,
    val currentQuestionElapsedSeconds: Long = 0L,
    val totalElapsedSeconds: Long = 0L,
    val timedOut: Boolean = false,
    val sharedExam: SharedExam? = null,
    val participantName: String = "",
    val shareConsentGranted: Boolean = false,
    val attemptUploaded: Boolean = false,
    val attemptUploading: Boolean = false,
    val uploadedAttemptId: String? = null,
    val uploadErrorMessage: String? = null,
    val perQuestionSeconds: List<Long> = emptyList(),
    val evaluationSummary: ExamEvaluationSummary? = null,
) {
    val isSharedExam: Boolean get() = sharedExam != null
}
