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
