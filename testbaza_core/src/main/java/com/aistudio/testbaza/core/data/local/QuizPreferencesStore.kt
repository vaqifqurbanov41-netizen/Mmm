package com.aistudio.testbaza.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aistudio.testbaza.core.domain.TestBazaThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.testBazaDataStore by preferencesDataStore(name = "testbaza_prefs")

class QuizPreferencesStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_BANK_ID = longPreferencesKey("active_bank_id")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
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
}
