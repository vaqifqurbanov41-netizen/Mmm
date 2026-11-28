package com.aistudio.testbaza.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aistudio.testbaza.core.domain.ExamConfig
import com.aistudio.testbaza.core.domain.TestBazaThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.testBazaDataStore by preferencesDataStore(name = "testbaza_prefs")

class QuizPreferencesStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_BANK_ID = longPreferencesKey("active_bank_id")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val EXAM_QUESTION_COUNT = intPreferencesKey("exam_question_count")
        val EXAM_DURATION_MINUTES = intPreferencesKey("exam_duration_minutes")
    }

    val themeModeFlow: Flow<TestBazaThemeMode> = context.testBazaDataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE]
        TestBazaThemeMode.entries.firstOrNull { it.name == raw } ?: TestBazaThemeMode.DARK
    }

    val activeBankIdFlow: Flow<Long?> = context.testBazaDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_BANK_ID]
    }

    val shuffleEnabledFlow: Flow<Boolean> = context.testBazaDataStore.data.map { prefs ->
        prefs[Keys.SHUFFLE_ENABLED] ?: true
    }

    val examConfigFlow: Flow<ExamConfig> = context.testBazaDataStore.data.map { prefs ->
        ExamConfig(
            questionCount = (prefs[Keys.EXAM_QUESTION_COUNT] ?: 20)
                .coerceIn(ExamConfig.MIN_QUESTIONS, ExamConfig.MAX_QUESTIONS),
            durationMinutes = (prefs[Keys.EXAM_DURATION_MINUTES] ?: 20)
                .coerceIn(ExamConfig.MIN_DURATION_MINUTES, ExamConfig.MAX_DURATION_MINUTES),
        )
    }

    suspend fun setThemeMode(mode: TestBazaThemeMode) {
        context.testBazaDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setActiveBankId(bankId: Long) {
        context.testBazaDataStore.edit { it[Keys.ACTIVE_BANK_ID] = bankId }
    }

    suspend fun clearActiveBankId() {
        context.testBazaDataStore.edit { it.remove(Keys.ACTIVE_BANK_ID) }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.testBazaDataStore.edit { it[Keys.SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setExamQuestionCount(count: Int) {
        val clamped = count.coerceIn(ExamConfig.MIN_QUESTIONS, ExamConfig.MAX_QUESTIONS)
        context.testBazaDataStore.edit { it[Keys.EXAM_QUESTION_COUNT] = clamped }
    }

    suspend fun setExamDurationMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(ExamConfig.MIN_DURATION_MINUTES, ExamConfig.MAX_DURATION_MINUTES)
        context.testBazaDataStore.edit { it[Keys.EXAM_DURATION_MINUTES] = clamped }
    }
}
