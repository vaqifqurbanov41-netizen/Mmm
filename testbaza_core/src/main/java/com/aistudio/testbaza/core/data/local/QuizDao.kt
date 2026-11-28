package com.aistudio.testbaza.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aistudio.testbaza.core.domain.QuestionMasteryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    @Query(
        """
        SELECT b.id, b.title, b.createdAtEpochMs, b.isDefault,
               COUNT(q.rowId) AS questionCount
        FROM quiz_banks b
        LEFT JOIN quiz_questions q ON q.bankId = b.id
        GROUP BY b.id
        ORDER BY b.isDefault DESC, b.createdAtEpochMs DESC
        """
    )
    fun observeBanksWithCounts(): Flow<List<QuizBankWithCount>>

    @Query("SELECT COUNT(*) FROM quiz_banks")
    suspend fun getBankCount(): Int

    @Query("SELECT * FROM quiz_banks WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultBank(): QuizBankEntity?

    @Query("SELECT * FROM quiz_questions WHERE bankId = :bankId ORDER BY rowId ASC")
    fun observeQuestionsForBank(bankId: Long): Flow<List<QuizQuestionEntity>>

    @Query("SELECT * FROM quiz_questions WHERE bankId = :bankId ORDER BY rowId ASC")
    suspend fun getQuestionsForBankOnce(bankId: Long): List<QuizQuestionEntity>

    @Query("SELECT questionNumber FROM quiz_questions WHERE bankId = :bankId")
    suspend fun getQuestionNumbersForBank(bankId: Long): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBank(bank: QuizBankEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuizQuestionEntity>)

    @Transaction
    suspend fun insertBankWithQuestions(
        bank: QuizBankEntity,
        questions: List<QuizQuestionEntity>,
    ): Long {
        val bankId = insertBank(bank)
        insertQuestions(questions.map { it.copy(bankId = bankId) })
        return bankId
    }

    @Query("DELETE FROM quiz_banks WHERE id = :bankId")
    suspend fun deleteBank(bankId: Long)

    @Query("DELETE FROM quiz_banks WHERE isDefault = 1")
    suspend fun deleteDefaultBanks()

    @Query("UPDATE quiz_questions SET isExam = :isExam WHERE rowId = :rowId")
    suspend fun updateExamFlag(rowId: Long, isExam: Boolean)

    @Query("UPDATE quiz_questions SET correctText = :correctText, hasConfirmedKey = 1 WHERE rowId = :rowId")
    suspend fun updateAnswerKey(rowId: Long, correctText: String)

    @Query("UPDATE quiz_questions SET masteryStatus = :status, wrongCount = :wrongCount, correctStreak = :correctStreak WHERE rowId = :rowId")
    suspend fun updateMastery(
        rowId: Long,
        status: QuestionMasteryStatus,
        wrongCount: Int,
        correctStreak: Int,
    )

    @Query("UPDATE quiz_questions SET masteryStatus = 'NEW', wrongCount = 0, correctStreak = 0 WHERE bankId = :bankId")
    suspend fun resetMasteryForBank(bankId: Long)
}
