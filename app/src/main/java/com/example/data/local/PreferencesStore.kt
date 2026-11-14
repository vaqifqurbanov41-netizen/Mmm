package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "agro_prefs")

class PreferencesStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val THEME = stringPreferencesKey("theme_mode")
        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LON = doublePreferencesKey("last_lon")
    }

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[Keys.LANGUAGE])
    }

    val themeFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        ThemePreference.fromCode(prefs[Keys.THEME])
    }

    val coordinatesFlow: Flow<Pair<Double, Double>> = context.dataStore.data.map { prefs ->
        val lat = prefs[Keys.LAST_LAT] ?: 40.6782
        val lon = prefs[Keys.LAST_LON] ?: 46.3606
        lat to lon
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = theme.code }
    }

    suspend fun setCoordinates(lat: Double, lon: Double) {
        context.dataStore.edit {
            it[Keys.LAST_LAT] = lat
            it[Keys.LAST_LON] = lon
        }
    }
}
