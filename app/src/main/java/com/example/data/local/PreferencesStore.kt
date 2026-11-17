package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "agro_prefs")

fun normalizeReminderDays(days: Int?): Int = when (days) {
    1, 7, 30 -> days
    else -> 7
}

class PreferencesStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val THEME = stringPreferencesKey("theme_mode")
        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LON = doublePreferencesKey("last_lon")
        val EQUIPMENT_REMINDER_DAYS = intPreferencesKey("equipment_reminder_days")
        val HIDDEN_HOME_MODULES = stringSetPreferencesKey("hidden_home_modules")
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

    val equipmentReminderDaysFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        normalizeReminderDays(prefs[Keys.EQUIPMENT_REMINDER_DAYS])
    }

    val hiddenModulesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.HIDDEN_HOME_MODULES] ?: emptySet()
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

    suspend fun setEquipmentReminderDays(days: Int) {
        context.dataStore.edit {
            it[Keys.EQUIPMENT_REMINDER_DAYS] = normalizeReminderDays(days)
        }
    }

    suspend fun setModuleVisible(route: String, visible: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_HOME_MODULES]?.toMutableSet() ?: mutableSetOf()
            if (visible) {
                current.remove(route)
            } else {
                current.add(route)
            }
            prefs[Keys.HIDDEN_HOME_MODULES] = current
        }
    }

    suspend fun showAllModules() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.HIDDEN_HOME_MODULES)
        }
    }
}
