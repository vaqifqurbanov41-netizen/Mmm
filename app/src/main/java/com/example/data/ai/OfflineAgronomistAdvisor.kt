package com.example.data.ai

import com.example.data.model.CropProtectionCatalog
import com.example.data.model.CropThreatEntry
import com.example.data.model.SeedCatalog
import com.example.data.model.ThreatKind
import com.example.i18n.AppLanguage
import com.example.i18n.StringsAz
import com.example.i18n.StringsEn
import com.example.i18n.StringsRu

object OfflineAgronomistAdvisor {

    fun buildPromptContext(question: String, language: AppLanguage): String {
        val matched = findRelevantThreats(question).take(4)
        if (matched.isEmpty()) return ""
        return buildString {
            appendLine("Reference local crop-protection catalog (prefer active ingredients first):")
            matched.forEach { entry ->
                val cropName = CropProtectionCatalog.crops.firstOrNull { it.id == entry.cropId }?.name?.get(language).orEmpty()
                appendLine("- $cropName | ${entry.name.get(language)} | Active ingredient: ${entry.activeIngredient.get(language)} | Tip: ${entry.guidance.get(language)}")
            }
        }
    }

    fun answerOffline(
        question: String,
        language: AppLanguage,
        aiFailureNote: String? = null,
    ): String {
        val normalizedQ = CropProtectionCatalog.normalizeSearch(question)
        val strings = when (language) {
            AppLanguage.AZ -> StringsAz
            AppLanguage.EN -> StringsEn
            AppLanguage.RU -> StringsRu
        }

        // 1. Check banned pesticide mentions first
        val bannedMatch = strings.bannedPesticides.firstOrNull { banned ->
            val nameMatch = CropProtectionCatalog.normalizeSearch(banned.name)
                .split(" ", "/", "(", ")")
                .any { it.length >= 3 && normalizedQ.contains(it) }
            val aliasMatch = banned.aliases.any { alias ->
                normalizedQ.contains(CropProtectionCatalog.normalizeSearch(alias))
            }
            nameMatch || aliasMatch
        }
        if (bannedMatch != null) {
            val base = when (language) {
                AppLanguage.AZ -> """
                    🚫 Oflayn Aqronom Xəbərdarlığı — Qadağan Olunmuş Maddə!

                    • Maddə: ${bannedMatch.name}
                    • Səbəb: ${bannedMatch.reason}
                    • Status: ${bannedMatch.legalStatus}
                    • Tövsiyə: Pestisid bölməsindən bitki və xəstəliyə uyğun icazəli ana maddəni seçin.
                """.trimIndent()
                AppLanguage.EN -> """
                    🚫 Offline Agronomist Warning — Banned Substance!

                    • Substance: ${bannedMatch.name}
                    • Hazard: ${bannedMatch.reason}
                    • Status: ${bannedMatch.legalStatus}
                    • Advice: Check the Pesticide section for approved active ingredients per crop.
                """.trimIndent()
                AppLanguage.RU -> """
                    🚫 Офлайн Агроном — Запрещённое вещество!

                    • Вещество: ${bannedMatch.name}
                    • Причина: ${bannedMatch.reason}
                    • Статус: ${bannedMatch.legalStatus}
                    • Совет: Выберите разрешённое действующее вещество в разделе Пестициды.
                """.trimIndent()
            }
            return appendNoteIfNeeded(base, aiFailureNote, language)
        }

        // 2. Match diseases & pests from CropProtectionCatalog
        val threats = findRelevantThreats(question).take(3)
        if (threats.isNotEmpty()) {
            val header = when (language) {
                AppLanguage.AZ -> "📚 Oflayn Aqronom Bələdçisi (Bitki Mühafizə Bazası)"
                AppLanguage.EN -> "📚 Offline Agronomist Guide (Local Crop Protection Base)"
                AppLanguage.RU -> "📚 Офлайн Агроном (База защиты растений)"
            }
            val body = buildString {
                appendLine(header)
                threats.forEachIndexed { idx, t ->
                    val cropLabel = CropProtectionCatalog.crops
                        .firstOrNull { it.id == t.cropId }
                        ?.name?.get(language)
                        .orEmpty()
                    val kindLabel = when (t.kind) {
                        ThreatKind.DISEASE -> when (language) {
                            AppLanguage.AZ -> "Xəstəlik"
                            AppLanguage.EN -> "Disease"
                            AppLanguage.RU -> "Болезнь"
                        }
                        ThreatKind.PEST -> when (language) {
                            AppLanguage.AZ -> "Zərərverici"
                            AppLanguage.EN -> "Pest"
                            AppLanguage.RU -> "Вредитель"
                        }
                    }
                    appendLine()
                    appendLine("${idx + 1}. $cropLabel — ${t.name.get(language)} ($kindLabel)")
                    when (language) {
                        AppLanguage.AZ -> {
                            appendLine("• Əlamətlər: ${t.symptoms.az}")
                            appendLine("• Tövsiyə olunan ana maddə: ${t.activeIngredient.az}")
                            appendLine("• Tətbiq məsləhəti: ${t.guidance.az}")
                        }
                        AppLanguage.EN -> {
                            appendLine("• Symptoms: ${t.symptoms.en}")
                            appendLine("• Active ingredient: ${t.activeIngredient.en}")
                            appendLine("• Guidance: ${t.guidance.en}")
                        }
                        AppLanguage.RU -> {
                            appendLine("• Симптомы: ${t.symptoms.ru}")
                            appendLine("• Действующее вещество: ${t.activeIngredient.ru}")
                            appendLine("• Рекомендация: ${t.guidance.ru}")
                        }
                    }
                }
                appendLine()
                append(
                    when (language) {
                        AppLanguage.AZ -> "💡 Qeyd: Seçilmiş ana maddəyə uyğun preparatları və maksimum hektar normasını «Pestisid → Preparat axtarışı» bölməsində yoxlaya bilərsiniz."
                        AppLanguage.EN -> "💡 Tip: Search matching commercial products and max hectare rates by active ingredient in «Pesticides → Preparation Search»."
                        AppLanguage.RU -> "💡 Совет: Подобрать препараты и проверить норму по действующему веществу можно в «Пестициды → Поиск препаратов»."
                    }
                )
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 3. Check seed / variety questions
        val matchingVarieties = SeedCatalog.varieties.filter { v ->
            val crop = SeedCatalog.crops.firstOrNull { it.id == v.cropId }
            val tokens = normalizedQ.split(Regex("\\s+")).filter { it.length >= 3 }
            tokens.any { tok ->
                CropProtectionCatalog.normalizeSearch(v.name).contains(tok) ||
                    CropProtectionCatalog.normalizeSearch(crop?.name?.az.orEmpty()).contains(tok) ||
                    CropProtectionCatalog.normalizeSearch(crop?.name?.en.orEmpty()).contains(tok) ||
                    CropProtectionCatalog.normalizeSearch(crop?.name?.ru.orEmpty()).contains(tok)
            }
        }.take(3)
        if (matchingVarieties.isNotEmpty() &&
            (normalizedQ.contains("sort") || normalizedQ.contains("toxum") || normalizedQ.contains("sepin") ||
                normalizedQ.contains("seed") || normalizedQ.contains("variety") || normalizedQ.contains("семен") || normalizedQ.contains("сорт"))
        ) {
            val body = buildString {
                appendLine(
                    when (language) {
                        AppLanguage.AZ -> "🌱 Oflayn Toxum və Sort Bələdçisi"
                        AppLanguage.EN -> "🌱 Offline Seed & Variety Guide"
                        AppLanguage.RU -> "🌱 Офлайн гид по семенам и сортам"
                    }
                )
                matchingVarieties.forEach { v ->
                    appendLine()
                    appendLine("• ${v.name} — ${v.summary.get(language)}")
                    appendLine("  ${v.sowingWindow.get(language)} | ${v.seedingRate.get(language)}")
                    appendLine("  ${v.agronomyTip.get(language)}")
                }
            }
            return appendNoteIfNeeded(body, aiFailureNote, language)
        }

        // 4. General helpful offline summary
        val fallback = when (language) {
            AppLanguage.AZ -> """
                📚 Oflayn Aqronom Bələdçisi (Yerli Baza aktivdir)

                Sualınızda konkret bitki və ya əlamət göstərin — məsələn:
                • «Pomidor yarpaqları saralır / fitoftoroz»
                • «Buğda yarpaqlarında pas xəstəliyi»
                • «Pambıq sovkası / mənənə»
                • «Kartof kolorado böcəyi»
                • «Üzüm mildiu / oidium»
                • «Alma dəmgil / alma qurdu»

                Hazırda yerli bazada 10 əsas bitki üzrə xəstəliklər, zərərvericilər və təsiredici ana maddələr saxlanılır. Pestisid bölməsindən də bitki → xəstəlik → ana maddə ardıcıllığı ilə baxa bilərsiniz.
            """.trimIndent()
            AppLanguage.EN -> """
                📚 Offline Agronomist Guide (Local Database Active)

                Include a specific crop, pest, or symptom in your question — for example:
                • "Tomato late blight / yellow leaves"
                • "Wheat rust / sunn pest"
                • "Cotton bollworm / aphids"
                • "Potato Colorado beetle"
                • "Grape downy mildew / oidium"

                Our local protection catalog covers 10 key crops with symptoms, active ingredients, and safety guidance even without internet access.
            """.trimIndent()
            AppLanguage.RU -> """
                📚 Офлайн Агроном (Локальная база активна)

                Укажите культуру, вредителя или симптом — например:
                • «Фитофтороз томата / жёлтые листья»
                • «Ржавчина пшеницы / клоп-черепашка»
                • «Хлопковая совка / тля»
                • «Колорадский жук на картофеле»
                • «Милдью и оидиум винограда»

                Локальная база содержит болезни, вредителей и действующие вещества по 10 культурам и работает полностью без интернета.
            """.trimIndent()
        }
        return appendNoteIfNeeded(fallback, aiFailureNote, language)
    }

    fun findRelevantThreats(query: String): List<CropThreatEntry> {
        val normQuery = CropProtectionCatalog.normalizeSearch(query)
        if (normQuery.isBlank()) return emptyList()

        val tokens = normQuery
            .split(Regex("[^a-zа-я0-9]+"))
            .filter { it.length >= 3 }
            .distinct()

        // Boost by crop match + symptom/name/active-ingredient match
        val matchedCropIds = CropProtectionCatalog.crops.filter { crop ->
            val allNames = listOf(crop.name.az, crop.name.en, crop.name.ru)
                .map { CropProtectionCatalog.normalizeSearch(it) }
            allNames.any { cname ->
                normQuery.contains(cname.take(4)) || tokens.any { tok -> cname.startsWith(tok.take(4)) }
            }
        }.map { it.id }.toSet()

        val scored = CropProtectionCatalog.threats.map { threat ->
            var score = 0
            if (threat.cropId in matchedCropIds) score += 6

            val searchable = CropProtectionCatalog.normalizeSearch(
                listOf(
                    threat.name.az, threat.name.en, threat.name.ru,
                    threat.symptoms.az, threat.symptoms.en, threat.symptoms.ru,
                    threat.activeIngredient.az, threat.activeIngredient.en, threat.activeIngredient.ru,
                    threat.searchIngredientKey,
                ).joinToString(" ")
            )

            tokens.forEach { tok ->
                val stem = if (tok.length >= 5) tok.take(5) else tok
                if (searchable.contains(tok)) score += 4
                else if (stem.length >= 4 && searchable.contains(stem)) score += 2
            }

            // Helpful symptom synonym boosts in Azerbaijani/English/Russian
            if ((normQuery.contains("saral") || normQuery.contains("leke") || normQuery.contains("quru") || normQuery.contains("yellow") || normQuery.contains("spot") || normQuery.contains("пятн")) &&
                threat.kind == ThreatKind.DISEASE
            ) {
                score += 2
            }
            if ((normQuery.contains("qurd") || normQuery.contains("bocek") || normQuery.contains("hesarat") || normQuery.contains("pest") || normQuery.contains("worm") || normQuery.contains("вред") || normQuery.contains("жук")) &&
                threat.kind == ThreatKind.PEST
            ) {
                score += 2
            }
            threat to score
        }

        val minThreshold = if (matchedCropIds.isNotEmpty()) 6 else 4
        return scored
            .filter { it.second >= minThreshold }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun appendNoteIfNeeded(
        base: String,
        aiFailureNote: String?,
        language: AppLanguage,
    ): String {
        val err = aiFailureNote?.trim().orEmpty()
        if (err.isBlank()) return base
        val prefix = when (language) {
            AppLanguage.AZ -> "ℹ️ Bulud AI hazırda əlçatan olmadığından (və ya oflayn rejimdə) yerli baza üzrə cavab verildi"
            AppLanguage.EN -> "ℹ️ Answered from local catalog because Cloud AI is unavailable or offline"
            AppLanguage.RU -> "ℹ️ Ответ сформирован по локальной базе (облачный AI недоступен или офлайн)"
        }
        return "$base\n\n$prefix ($err)"
    }
}
