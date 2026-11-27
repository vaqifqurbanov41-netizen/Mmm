package com.aistudio.testbaza.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizQuestion
import org.json.JSONArray

@Entity(tableName = "quiz_banks")
data class QuizBankEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val createdAtEpochMs: Long,
    val isDefault: Boolean = false,
)

@Entity(
    tableName = "quiz_questions",
    foreignKeys = [
        ForeignKey(
            entity = QuizBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bankId")],
)
data class QuizQuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0L,
    val bankId: Long,
    val questionNumber: Int,
    val category: String,
    val isExam: Boolean,
    val questionText: String,
    val allOptions: List<String>,
    val correctText: String,
    val hasConfirmedKey: Boolean,
    val masteryStatus: QuestionMasteryStatus = QuestionMasteryStatus.NEW,
    val wrongCount: Int = 0,
    val correctStreak: Int = 0,
)

data class QuizBankWithCount(
    val id: Long,
    val title: String,
    val createdAtEpochMs: Long,
    val isDefault: Boolean,
    val questionCount: Int,
)

fun QuizBankWithCount.toDomain(): QuizBank = QuizBank(
    id = id,
    title = title,
    createdAtEpochMs = createdAtEpochMs,
    isDefault = isDefault,
    questionCount = questionCount,
)

fun QuizQuestionEntity.toDomain(): QuizQuestion = QuizQuestion(
    rowId = rowId,
    bankId = bankId,
    questionNumber = questionNumber,
    category = category,
    isExam = isExam,
    questionText = questionText,
    allOptions = allOptions,
    correctText = correctText,
    hasConfirmedKey = hasConfirmedKey,
    masteryStatus = masteryStatus,
    wrongCount = wrongCount,
    correctStreak = correctStreak,
)

class QuizTypeConverters {
    @TypeConverter
    fun fromStringList(list: List<String>): String = JSONArray(list).toString()

    @TypeConverter
    fun toStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return List(array.length()) { index -> array.optString(index, "") }
    }

    @TypeConverter
    fun fromMasteryStatus(status: QuestionMasteryStatus): String = status.name

    @TypeConverter
    fun toMasteryStatus(raw: String): QuestionMasteryStatus =
        QuestionMasteryStatus.entries.firstOrNull { it.name == raw } ?: QuestionMasteryStatus.NEW
}
