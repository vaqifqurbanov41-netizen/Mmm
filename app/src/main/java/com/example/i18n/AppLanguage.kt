package com.example.i18n

import java.util.Locale

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val locale: Locale,
) {
    AZ("az", "Azərbaycan dili", Locale.forLanguageTag("az-AZ")),
    EN("en", "English", Locale.US),
    RU("ru", "Русский", Locale.forLanguageTag("ru-RU"));

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AZ
    }
}
