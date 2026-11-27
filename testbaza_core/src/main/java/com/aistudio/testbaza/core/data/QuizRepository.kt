package com.aistudio.testbaza.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aistudio.testbaza.core.R
import com.aistudio.testbaza.core.data.local.QuizBankEntity
import com.aistudio.testbaza.core.data.local.QuizDao
import com.aistudio.testbaza.core.data.local.QuizQuestionEntity
import com.aistudio.testbaza.core.data.local.toDomain
import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.ParsedQuizResult
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizDocumentParser
import com.aistudio.testbaza.core.domain.QuizQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

class QuizRepository(
    private val context: Context,
    private val dao: QuizDao,
) {
    companion object {
        const val DEFAULT_BANK_TITLE = "torpaq münbitliyi test A variant qiyabi"
    }

    val banksFlow: Flow<List<QuizBank>> =
        dao.observeBanksWithCounts().map { rows -> rows.map { it.toDomain() } }

    fun observeQuestions(bankId: Long): Flow<List<QuizQuestion>> =
        dao.observeQuestionsForBank(bankId).map { rows -> rows.map { it.toDomain() } }

    suspend fun ensureDefaultBankSeeded(): Long = withContext(Dispatchers.IO) {
        val rawJson = context.resources.openRawResource(R.raw.default_questions)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val jsonArray = JSONArray(rawJson)
        val existingDefault = dao.getDefaultBank()

        fun buildEntities(targetBankId: Long): List<QuizQuestionEntity> = buildList(jsonArray.length()) {
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val optsJson = obj.getJSONArray("allOptions")
                val options = List(optsJson.length()) { idx -> optsJson.getString(idx) }
                add(
                    QuizQuestionEntity(
                        bankId = targetBankId,
                        questionNumber = obj.optInt("id", i + 1),
                        category = obj.optString("cat", DEFAULT_BANK_TITLE),
                        isExam = obj.optBoolean("isExam", false),
                        questionText = obj.optString("q", ""),
                        allOptions = options,
                        correctText = obj.optString("correctText", options.firstOrNull().orEmpty()),
                        hasConfirmedKey = obj.optBoolean("hasConfirmedKey", true),
                    )
                )
            }
        }

        if (existingDefault != null) {
            val existingNumbers = dao.getQuestionNumbersForBank(existingDefault.id).toHashSet()
            if (existingNumbers.size < jsonArray.length()) {
                val missing = buildEntities(existingDefault.id)
                    .filter { it.questionNumber !in existingNumbers }
                if (missing.isNotEmpty()) {
                    dao.insertQuestions(missing)
                }
            }
            return@withContext existingDefault.id
        }

        dao.insertBankWithQuestions(
            bank = QuizBankEntity(
                title = DEFAULT_BANK_TITLE,
                createdAtEpochMs = System.currentTimeMillis(),
                isDefault = true,
            ),
            questions = buildEntities(0L),
        )
    }

    suspend fun importDocumentUri(
        uri: Uri,
        rule: AnswerRule,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveDisplayName(uri) ?: "Yeni Test Bazası"
            val lower = fileName.lowercase()
            val cleanTitle = fileName.replace(Regex("""\.(docx|pdf|doc|txt)$""", RegexOption.IGNORE_CASE), "").trim()
                .ifEmpty { "Yeni Test Bazası" }

            if (lower.endsWith(".doc") && !lower.endsWith(".docx")) {
                throw IllegalArgumentException(
                    "Bu fayl köhnə .doc formatındadır. Word-də .docx və ya .pdf kimi saxlayın, yaxud mətni kopyalayıb 'Mətn Yapışdır' hissəsinə atın."
                )
            }

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Fayl oxuna bilmədi.")
            if (bytes.size < 4) {
                throw IllegalArgumentException("Seçilən fayl boşdur.")
            }

            val isPdf = lower.endsWith(".pdf") || (
                bytes[0] == 0x25.toByte() &&
                    bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x44.toByte() &&
                    bytes[3] == 0x46.toByte()
                )
            val isZip = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

            val lines = when {
                isPdf -> {
                    val extracted = bytes.inputStream().use { QuizDocumentParser.extractPdfLines(context, it) }
                    if (extracted.isEmpty()) {
                        throw IllegalArgumentException(
                            "Bu PDF faylı skan olunmuş şəkil formatındadır (mətn qatı yoxdur). Mətn əsaslı PDF seçin və ya sualları 'Mətn Yapışdır' bölməsinə əlavə edin."
                        )
                    }
                    extracted
                }
                lower.endsWith(".txt") -> bytes.inputStream().use { QuizDocumentParser.extractTxtLines(it) }
                isZip || lower.endsWith(".docx") -> {
                    if (!isZip) {
                        throw IllegalArgumentException("Seçilən fayl dəstəklənən .docx, .pdf və ya .txt formatında deyil.")
                    }
                    val extracted = bytes.inputStream().use { QuizDocumentParser.extractDocxLines(it) }
                    if (extracted.isEmpty()) {
                        throw IllegalArgumentException(
                            "Word faylının içindən mətn oxuna bilmədi. Zəhmət olmasa sualları kopyalayıb 'Mətn Yapışdır' bölməsinə yapışdırın."
                        )
                    }
                    extracted
                }
                else -> throw IllegalArgumentException("Seçilən fayl dəstəklənən .docx, .pdf və ya .txt formatında deyil.")
            }

            val parsed = QuizDocumentParser.parseStructuredLines(lines, cleanTitle, rule).getOrThrow()
            saveParsedBank(parsed)
        }
    }

    suspend fun importRawText(
        rawText: String,
        title: String,
        rule: AnswerRule,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = QuizDocumentParser.parseRawText(
                rawText = rawText,
                title = title.ifBlank { "Yapışdırılmış Test Bazası" },
                rule = rule,
            ).getOrThrow()
            saveParsedBank(parsed)
        }
    }

    private suspend fun saveParsedBank(parsed: ParsedQuizResult): Long {
        val entities = parsed.questions.map { q ->
            QuizQuestionEntity(
                bankId = 0L,
                questionNumber = q.questionNumber,
                category = q.category,
                isExam = q.isExam,
                questionText = q.questionText,
                allOptions = q.allOptions,
                correctText = q.correctText,
                hasConfirmedKey = q.hasConfirmedKey,
            )
        }
        return dao.insertBankWithQuestions(
            bank = QuizBankEntity(
                title = parsed.title,
                createdAtEpochMs = System.currentTimeMillis(),
                isDefault = false,
            ),
            questions = entities,
        )
    }

    suspend fun toggleStar(question: QuizQuestion) = withContext(Dispatchers.IO) {
        dao.updateExamFlag(question.rowId, !question.isExam)
    }

    suspend fun updateSingleAnswerKey(rowId: Long, correctText: String) = withContext(Dispatchers.IO) {
        dao.updateAnswerKey(rowId, correctText)
    }

    suspend fun selectAllFirstOptions(bankId: Long) = withContext(Dispatchers.IO) {
        val list = dao.getQuestionsForBankOnce(bankId)
        for (q in list) {
            val first = q.allOptions.firstOrNull() ?: continue
            dao.updateAnswerKey(q.rowId, first)
        }
    }

    suspend fun recordAnswerOutcome(question: QuizQuestion, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val newStatus = if (isCorrect) QuestionMasteryStatus.MASTERED else QuestionMasteryStatus.WEAK
        val newWrongCount = if (isCorrect) question.wrongCount else question.wrongCount + 1
        val newStreak = if (isCorrect) question.correctStreak + 1 else 0
        dao.updateMastery(
            rowId = question.rowId,
            status = newStatus,
            wrongCount = newWrongCount,
            correctStreak = newStreak,
        )
    }

    suspend fun markMastered(question: QuizQuestion) = withContext(Dispatchers.IO) {
        dao.updateMastery(
            rowId = question.rowId,
            status = QuestionMasteryStatus.MASTERED,
            wrongCount = 0,
            correctStreak = maxOf(3, question.correctStreak + 1),
        )
    }

    suspend fun resetMastery(bankId: Long) = withContext(Dispatchers.IO) {
        dao.resetMasteryForBank(bankId)
    }

    suspend fun deleteBank(bankId: Long) = withContext(Dispatchers.IO) {
        dao.deleteBank(bankId)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
