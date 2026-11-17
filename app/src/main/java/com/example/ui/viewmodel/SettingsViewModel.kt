package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesStore
import com.example.i18n.AppLanguage
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesStore: PreferencesStore,
) : ViewModel() {

    val language: StateFlow<AppLanguage> = preferencesStore.languageFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppLanguage.AZ,
        )

    val themePreference: StateFlow<ThemePreference> = preferencesStore.themeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ThemePreference.LIGHT,
        )

    val coordinates: StateFlow<Pair<Double, Double>> = preferencesStore.coordinatesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 40.6782 to 46.3606,
        )

    val hiddenModules: StateFlow<Set<String>> = preferencesStore.hiddenModulesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptySet(),
        )

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesStore.setLanguage(language)
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            preferencesStore.setTheme(theme)
        }
    }

    fun toggleLightDark(isCurrentlyDark: Boolean) {
        val next = if (isCurrentlyDark) ThemePreference.LIGHT else ThemePreference.DARK
        setThemePreference(next)
    }

    fun saveCoordinates(lat: Double, lon: Double) {
        viewModelScope.launch {
            preferencesStore.setCoordinates(lat, lon)
        }
    }

    fun setModuleVisible(route: String, visible: Boolean) {
        viewModelScope.launch {
            preferencesStore.setModuleVisible(route, visible)
        }
    }

    fun showAllModules() {
        viewModelScope.launch {
            preferencesStore.showAllModules()
        }
    }
}
