package com.aistudio.testbaza.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aistudio.testbaza.core.data.local.ExamAttemptHistoryEntity
import com.aistudio.testbaza.core.data.local.QuizBankEntity
import com.aistudio.testbaza.core.data.local.QuizDao
import com.aistudio.testbaza.core.data.local.QuizQuestionEntity
import com.aistudio.testbaza.core.data.local.QuizSerializationHelpers
import com.aistudio.testbaza.core.data.local.toDomain
import com.aistudio.testbaza.core.domain.AnswerRule
import com.aistudio.testbaza.core.domain.ExamAttemptHistoryItem
import com.aistudio.testbaza.core.domain.ExamEvaluationSummary
import com.aistudio.testbaza.core.domain.ParsedQuizResult
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import com.aistudio.testbaza.core.domain.QuizBank
import com.aistudio.testbaza.core.domain.QuizDocumentParser
import com.aistudio.testbaza.core.domain.QuizQuestion
import com.aistudio.testbaza.core.domain.SessionQuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class QuizRepository(
    private val context: Context,
    private val dao: QuizDao,
) {
    val banksFlow: Flow<List<QuizBank>> =
        dao.observeBanksWithCounts().map { rows -> rows.map { it.toDomain() } }

    fun observeQuestions(bankId: Long): Flow<List<QuizQuestion>> =
        dao.observeQuestionsForBank(bankId).map { rows -> rows.map { it.toDomain() } }

    fun observeExamHistory(bankId: Long): Flow<List<ExamAttemptHistoryItem>> =
        dao.observeExamHistoryForBank(bankId).map { rows -> rows.map { it.toDomain() } }

    suspend fun getQuestionsOnce(bankId: Long): List<QuizQuestion> = withContext(Dispatchers.IO) {
        dao.getQuestionsForBankOnce(bankId).map { it.toDomain() }
    }

    suspend fun removeBundledDefaultBank() = withContext(Dispatchers.IO) {
        dao.deleteDefaultBanks()
    }

    suspend fun importDocumentUri(
        uri: Uri,
        rule: AnswerRule,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveDisplayName(uri) ?: "Yeni Test Bazası"
            val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            val lower = fileName.lowercase()
            val cleanTitle = fileName
                .replace(Regex("""\.(docx|pdf|doc|txt)$""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { "Yeni Test Bazası" }

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Fayl oxuna bilmədi. Zəhmət olmasa yenidən seçin.")
            if (bytes.size < 4) {
                throw IllegalArgumentException("Seçilən fayl boşdur.")
            }

            val isPdf = bytes.size >= 4 &&
                bytes[0] == 0x25.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x44.toByte() &&
                bytes[3] == 0x46.toByte()

            val isZip = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

            // Legacy binary OLE2 (.doc) signature: D0 CF 11 E0
            val isLegacyBinaryDoc = bytes.size >= 4 &&
                bytes[0] == 0xD0.toByte() &&
                bytes[1] == 0xCF.toByte() &&
                bytes[2] == 0x11.toByte() &&
                bytes[3] == 0xE0.toByte()

            if (isLegacyBinaryDoc || (lower.endsWith(".doc") && !lower.endsWith(".docx") && !isZip)) {
                throw IllegalArgumentException(
                    "Bu fayl köhnə .doc formatındadır. Word-də .docx və ya .pdf kimi saxlayın, yaxud mətni kopyalayıb 'Mətn Yapışdır' hissəsinə atın."
                )
            }

            val isDocx = isZip ||
                lower.endsWith(".docx") ||
                mimeType.contains("wordprocessingml")

            val isTxt = lower.endsWith(".txt") ||
                mimeType.startsWith("text/") ||
                (!isPdf && !isZip && looksLikePlainText(bytes))

            val lines = when {
                isPdf || (lower.endsWith(".pdf") && !isZip) -> {
                    if (!isPdf) {
                        throw IllegalArgumentException("Seçilən fayl düzgün PDF formatında deyil.")
                    }
                    val extracted = bytes.inputStream().use { QuizDocumentParser.extractPdfLines(context, it) }
                    if (extracted.isEmpty()) {
                        throw IllegalArgumentException(
                            "Bu PDF faylı skan olunmuş şəkil formatındadır (mətn qatı yoxdur). Mətn əsaslı PDF seçin və ya sualları 'Mətn Yapışdır' bölməsinə əlavə edin."
                        )
                    }
                    extracted
                }
                isDocx -> {
                    if (!isZip) {
                        throw IllegalArgumentException("Seçilən Word (.docx) faylı zədələnib və ya dəstəklənməyən formatdadır.")
                    }
                    val extracted = bytes.inputStream().use { QuizDocumentParser.extractDocxLines(it) }
                    if (extracted.isEmpty()) {
                        throw IllegalArgumentException(
                            "Word (.docx) faylından mətn oxuna bilmədi. Faylın içi cədvəl/şəkil deyilsə mətni kopyalayıb 'Mətn Yapışdır' bölməsinə əlavə edin."
                        )
                    }
                    extracted
                }
                isTxt -> bytes.inputStream().use { QuizDocumentParser.extractTxtLines(it) }
                else -> throw IllegalArgumentException(
                    "Dəstəklənməyən fayl formatı. Zəhmət olmasa .docx (Word), .pdf və ya .txt faylı seçin."
                )
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

    suspend fun recordAnswerOutcome(
        question: QuizQuestion,
        isCorrect: Boolean,
        spentSeconds: Long? = null,
    ) = withContext(Dispatchers.IO) {
        val newStatus = if (isCorrect) QuestionMasteryStatus.MASTERED else QuestionMasteryStatus.WEAK
        val newWrongCount = if (isCorrect) question.wrongCount else question.wrongCount + 1
        val newStreak = if (isCorrect) question.correctStreak + 1 else 0
        dao.recordAttemptOutcome(
            rowId = question.rowId,
            status = newStatus,
            wrongCount = newWrongCount,
            correctStreak = newStreak,
            correctIncrement = if (isCorrect) 1 else 0,
        )
        if (spentSeconds != null && spentSeconds > 0L) {
            dao.addQuestionTiming(rowId = question.rowId, spentSeconds = spentSeconds)
        }
    }

    suspend fun recordBatchQuestionTimings(
        items: List<SessionQuestionItem>,
        perQuestionSeconds: List<Long>,
    ) = withContext(Dispatchers.IO) {
        items.forEachIndexed { idx, item ->
            val sec = perQuestionSeconds.getOrElse(idx) { 0L }
            if (item.question.rowId > 0L && sec > 0L) {
                dao.addQuestionTiming(rowId = item.question.rowId, spentSeconds = sec)
            }
        }
    }

    suspend fun saveExamHistoryRecord(
        bankId: Long,
        bankTitle: String,
        isTimedExam: Boolean,
        summary: ExamEvaluationSummary,
        totalElapsedSeconds: Long,
        durationLimitMinutes: Int,
        timedOut: Boolean,
    ) = withContext(Dispatchers.IO) {
        dao.insertExamHistory(
            ExamAttemptHistoryEntity(
                bankId = bankId,
                bankTitle = bankTitle.ifBlank { "Testbaza" },
                createdAtEpochMs = System.currentTimeMillis(),
                isTimedExam = isTimedExam,
                totalQuestions = summary.totalQuestions,
                correctCount = summary.correctCount,
                wrongCount = summary.wrongCount,
                unansweredCount = summary.unansweredCount,
                totalElapsedSeconds = totalElapsedSeconds.coerceAtLeast(0L),
                durationLimitMinutes = durationLimitMinutes.coerceAtLeast(0),
                timedOut = timedOut,
                wrongDetailsJson = QuizSerializationHelpers.encodeWrongDetails(summary.wrongDetails),
                questionTimingsJson = QuizSerializationHelpers.encodeQuestionTimings(summary.questionTimings),
            )
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

    suspend fun clearBankStatistics(bankId: Long) = withContext(Dispatchers.IO) {
        dao.resetStatisticsForBank(bankId)
        dao.deleteExamHistoryForBank(bankId)
    }

    suspend fun deleteBank(bankId: Long) = withContext(Dispatchers.IO) {
        dao.deleteBank(bankId)
    }

    private fun looksLikePlainText(bytes: ByteArray): Boolean {
        val sampleLen = minOf(bytes.size, 512)
        var controlCount = 0
        for (i in 0 until sampleLen) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x09 || (b in 0x0E..0x1F && b != 0x1B)) {
                controlCount++
            }
        }
        return controlCount <= sampleLen / 20
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
