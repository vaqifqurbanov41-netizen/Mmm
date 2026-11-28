package com.aistudio.testbaza.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.aistudio.testbaza.core.domain.ExamAttemptHistoryItem
import com.aistudio.testbaza.core.domain.ExamWrongDetail
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuestionTimingDetail
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizQuestion
import org.json.JSONArray
import org.json.JSONObject

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
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val totalTimeSeconds: Long = 0L,
    val lastTimeSeconds: Long = 0L,
    val timedCount: Int = 0,
)

@Entity(
    tableName = "exam_history",
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
data class ExamAttemptHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
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
    val wrongDetailsJson: String,
    val questionTimingsJson: String,
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
    attemptCount = attemptCount,
    correctCount = correctCount,
    totalTimeSeconds = totalTimeSeconds,
    lastTimeSeconds = lastTimeSeconds,
    timedCount = timedCount,
)

fun ExamAttemptHistoryEntity.toDomain(): ExamAttemptHistoryItem = ExamAttemptHistoryItem(
    id = id,
    bankId = bankId,
    bankTitle = bankTitle,
    createdAtEpochMs = createdAtEpochMs,
    isTimedExam = isTimedExam,
    totalQuestions = totalQuestions,
    correctCount = correctCount,
    wrongCount = wrongCount,
    unansweredCount = unansweredCount,
    totalElapsedSeconds = totalElapsedSeconds,
    durationLimitMinutes = durationLimitMinutes,
    timedOut = timedOut,
    wrongDetails = QuizSerializationHelpers.decodeWrongDetails(wrongDetailsJson),
    questionTimings = QuizSerializationHelpers.decodeQuestionTimings(questionTimingsJson),
)

object QuizSerializationHelpers {
    fun encodeWrongDetails(list: List<ExamWrongDetail>): String {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(
                JSONObject().apply {
                    put("qNum", w.questionNumber)
                    put("qText", w.questionText)
                    put("sel", w.selectedAnswer)
                    put("cor", w.correctAnswer)
                    put("sec", w.timeSpentSeconds)
                }
            )
        }
        return arr.toString()
    }

    fun decodeWrongDetails(raw: String): List<ExamWrongDetail> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val obj = arr.optJSONObject(idx) ?: JSONObject()
                ExamWrongDetail(
                    questionNumber = obj.optInt("qNum", 0),
                    questionText = obj.optString("qText", ""),
                    selectedAnswer = obj.optString("sel", ""),
                    correctAnswer = obj.optString("cor", ""),
                    timeSpentSeconds = obj.optLong("sec", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeQuestionTimings(list: List<QuestionTimingDetail>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("qNum", t.questionNumber)
                    put("qText", t.questionText)
                    put("status", t.status)
                    put("sec", t.timeSpentSeconds)
                }
            )
        }
        return arr.toString()
    }

    fun decodeQuestionTimings(raw: String): List<QuestionTimingDetail> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val obj = arr.optJSONObject(idx) ?: JSONObject()
                QuestionTimingDetail(
                    questionNumber = obj.optInt("qNum", 0),
                    questionText = obj.optString("qText", ""),
                    status = obj.optString("status", "SKIPPED"),
                    timeSpentSeconds = obj.optLong("sec", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }
}

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
